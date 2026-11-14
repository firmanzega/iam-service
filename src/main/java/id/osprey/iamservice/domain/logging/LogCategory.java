package id.osprey.iamservice.domain.logging;

/**
 * Kategori log terstruktur. Setiap kategori dapat dirutekan ke tujuan yang
 * berbeda oleh operator (misalnya errors ke kanal pemantauan, generals ke
 * file umum).
 */
public enum LogCategory {
    ERRORS,
    GENERALS,
    INTEGRATIONS,
    TIMERS,
    EMAILS
}
