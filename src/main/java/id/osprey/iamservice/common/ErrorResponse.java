package id.osprey.iamservice.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        int status,
        String error,
        LocalDateTime timestamp,
        String path,
        String message,
        Map<String, String> details) {

    public static ErrorResponse of
            (HttpStatus status,
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
