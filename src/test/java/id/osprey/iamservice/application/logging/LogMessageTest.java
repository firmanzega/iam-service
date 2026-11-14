package id.osprey.iamservice.application.logging;

import id.osprey.iamservice.domain.logging.LogCategory;
import id.osprey.iamservice.domain.logging.LoggingService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * LogMessage menyepakati payload log kustom developer; default category
 * dan delegasi ke LoggingService adalah kontraknya.
 */
class LogMessageTest {

    @Test
    void convenienceConstructor_defaultsToGeneralsWithoutAttributes() {
        LogMessage message = new LogMessage("pesan lepas");

        assertEquals(LogCategory.GENERALS, message.category());
        assertEquals("pesan lepas", message.message());
        assertEquals(Map.of(), message.attributes());
    }

    @Test
    void send_delegatesToGeneralChannel() {
        LoggingService loggingService = mock(LoggingService.class);
        LogMessage message = new LogMessage(LogCategory.ERRORS, "pesan kustom", Map.of("k", "v"));

        message.send(loggingService);

        verify(loggingService).general("pesan kustom", null, null, Map.of("k", "v"));
    }
}
