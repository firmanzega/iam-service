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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Clock;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

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
        return request.getRequestURI().startsWith("/v1/auth/");
    }

    private boolean isRateLimited(HttpServletRequest request) {
        IamProperties.RateLimit limit = properties.rateLimit();
        if (limit == null || limit.maxRequests() <= 0) {
            return false;
        }

        long windowMillis = limit.window().toMillis();
        long now = clock.millis();
        long threshold = now - windowMillis;
        String key = clientKey(request);

        // Seluruh perubahan antrean dilakukan di dalam compute, sehingga
        // pemeriksaan batas dan penambahan waktu berlaku atomik per klien.
        // Pemindai entri basi juga memakai computeIfPresent pada kunci yang
        // sama, jadi tidak ada penambahan ke antrean yang sudah dilepas dari
        // peta.
        AtomicBoolean limited = new AtomicBoolean(false);

        requestTimestamps.compute(key, (client, timestamps) -> {
            Deque<Long> queue = timestamps == null ? new ArrayDeque<>() : timestamps;

            while (!queue.isEmpty() && queue.peekFirst() < threshold) {
                queue.pollFirst();
            }

            if (queue.size() >= limit.maxRequests()) {
                limited.set(true);
            } else {
                queue.addLast(now);
            }

            return queue;
        });

        return limited.get();
    }

    /**
     * Membuang klien yang tidak lagi punya permintaan di dalam jendela waktu.
     *
     * Kenapa perlu: peta menyimpan satu entri per alamat IP. Kalau entri
     * klien yang sudah tidak aktif dibiarkan, memorinya naik terus seiring
     * jumlah IP yang pernah memanggil layanan. Pemindai ini berjalan berkala,
     * bukan pada setiap permintaan, supaya tidak menambah biaya di jalur panas.
     */
    @Scheduled(fixedDelayString = "${iam.rate-limit.cleanup-interval:PT10M}")
    public void scheduledCleanup() {
        removeStaleClients();
    }

    /**
     * Menghapus klien basi dan mengembalikan jumlah entri yang dibuang.
     * Dipisahkan dari method terjadwal agar bisa diuji langsung.
     */
    int removeStaleClients() {
        IamProperties.RateLimit limit = properties.rateLimit();
        if (limit == null) {
            return 0;
        }

        long threshold = clock.millis() - limit.window().toMillis();
        int[] removed = {0};

        requestTimestamps.keySet().forEach(key -> requestTimestamps.computeIfPresent(key, (client, timestamps) -> {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < threshold) {
                timestamps.pollFirst();
            }

            if (timestamps.isEmpty()) {
                removed[0]++;
                return null;
            }

            return timestamps;
        }));

        return removed[0];
    }

    /**
     * Kunci pembatasan diambil dari alamat IP.
     *
     * Kenapa tidak membaca X-Forwarded-For sendiri: isi header itu bisa
     * dipalsukan kalau layanan masih bisa dijangkau langsung. Kepercayaan pada
     * header diserahkan ke container lewat
     * server.forward-headers-strategy=native, yang hanya memakainya bila
     * koneksi berasal dari proxy di jaringan internal. Tanpa pengaturan itu,
     * di belakang reverse proxy semua klien akan terlihat memakai satu alamat
     * dan berbagi kuota yang sama.
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
