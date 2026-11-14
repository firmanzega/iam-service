package id.osprey.iamservice.application.logging;

import id.osprey.iamservice.domain.logging.LoggingService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * LogMessage adalah pintu masuk pesan kustom developer ke saluran generals
 * (ADR-0017). Kontraknya: atribut bawaan kosong, delegasi ke general().
 */
class LogMessageTest {

    @Test
    void convenienceConstructor_defaultsToEmptyAttributes() {
        LogMessage message = new LogMessage("pesan lepas");

        assertEquals("pesan lepas", message.message());
        assertEquals(Map.of(), message.attributes());
    }

    @Test
    void send_delegatesToGeneralChannel() {
        LoggingService loggingService = mock(LoggingService.class);
        LogMessage message = new LogMessage("pesan kustom", Map.of("k", "v"));

        message.send(loggingService);

        verify(loggingService).general("pesan kustom", null, null, Map.of("k", "v"));
    }
}
