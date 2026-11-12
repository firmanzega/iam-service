package id.osprey.iamservice.config;

import id.osprey.iamservice.adapter.rest.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Membatasi jumlah permintaan dari satu sumber dalam rentang waktu tertentu.
 *
 * Kenapa perlu:
 *      - Endpoint login adalah sasaran utama serangan brute force. Tanpa
 *        pembatasan, penyerang bisa mencoba ribuan kata sandi per menit.
 *      - Pembatasan juga melindungi sumber daya layanan dari permintaan
 *        berlebihan.
 *
 * Batasan implementasi:
 *      - Penghitung disimpan di memori proses. Untuk deployment lebih dari satu
 *        instance, pembatasan ini hanya berlaku per instance. Yang benar adalah
 *        memakai penyimpanan bersama seperti Redis, atau limiter di depan
 *        layanan seperti API gateway.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final IamProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    /** Waktu Epoch milidetik dari setiap permintaan yang dihitung, dikelompokkan per klien. */
    private final Map<String, Deque<Long>> requestTimestamps = new ConcurrentHashMap<>();

    @Autowired
    public RateLimitFilter(IamProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    RateLimitFilter(IamProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        if (isProtectedPath(request) && isRateLimited(request)) {
            log.warn("Rate limit triggered for {} on {}", clientKey(request), request.getRequestURI());
            writeTooManyRequests(request, response);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Hanya endpoint autentikasi yang dilindungi rate limiter.
     *
     * Kenapa bukan semua endpoint:
     *      - Yang paling perlu dilindungi adalah percobaan login, karena di
     *        situ berlangsung serangan tebak kata sandi.
     *      - Batas permintaan untuk endpoint lain biasanya lebih tepat
     *        ditempatkan di gateway atau reverse proxy, karena di sana batasnya
     *        bisa berlaku lintas instance.
     */
    private boolean isProtectedPath(HttpServletRequest request) {
        return request.getRequestURI().startsWith("/auth/");
    }

    private boolean isRateLimited(HttpServletRequest request) {
        IamProperties.RateLimit limit = properties.rateLimit();
        if (limit == null || limit.maxRequests() <= 0) {
            return false;
        }

        long windowMillis = limit.window().toMillis();
        long now = clock.millis();
        long threshold = now - windowMillis;

        Deque<Long> timestamps = requestTimestamps.computeIfAbsent(clientKey(request), key -> new ArrayDeque<>());

        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < threshold) {
                timestamps.pollFirst();
            }

            if (timestamps.size() >= limit.maxRequests()) {
                return true;
            }

            timestamps.addLast(now);
            return false;
        }
    }

    /**
     * Kunci pembatasan diambil dari alamat IP. Header seperti X-Forwarded-For
     * sengaja tidak dipakai karena isinya bisa dipalsukan oleh penyerang.
     */
    private String clientKey(HttpServletRequest request) {
        return request.getRemoteAddr();
    }

    private void writeTooManyRequests(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        ErrorResponse body = ErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too many requests, please retry later",
                request.getRequestURI(),
                null);

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType("application/json");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
