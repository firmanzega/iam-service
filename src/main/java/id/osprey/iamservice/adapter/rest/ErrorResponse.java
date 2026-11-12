package id.osprey.iamservice.adapter.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Bentuk galat yang seragam untuk seluruh endpoint.
 *
 * @param status kode status HTTP
 * @param error nama status HTTP
 * @param timestamp waktu galat terjadi
 * @param path alamat endpoint yang gagal
 * @param message penjelasan singkat
 * @param details rincian per field, hanya ada untuk galat validasi
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        int status,
        String error,
        LocalDateTime timestamp,
        String path,
        String message,
        Map<String, String> details) {

    public static ErrorResponse of(
            HttpStatus status,
            String message,
            String path,
            Map<String, String> details) {
        return new ErrorResponse(
                status.value(),
                status.getReasonPhrase(),
                LocalDateTime.now(),
                path,
                message,
                details);
    }
}
