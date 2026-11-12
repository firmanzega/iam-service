package id.osprey.iamservice;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Memeriksa arah dependensi antar lapisan.
 *
 * Kenapa perlu test ini:
 *      - Aturan arsitektur yang hanya tertulis di dokumen akan lama-lama
 *        terkikis setiap kali ada penambahan kode. Aturan yang diuji otomatis
 *        tidak bisa dilanggar tanpa membuat build gagal.
 *      - Domain yang diam-diam mulai mengimpor framework adalah cara paling
 *        umum Clean Architecture bocor, dan paling sulit terlihat saat review.
 *
 * Aturan yang diperiksa:
 *      - Domain tidak boleh mengimpor framework apa pun, termasuk Spring Data.
 *        Tipe paginasi milik domain dipakai supaya aturan ini bisa ditegakkan
 *        tanpa pengecualian.
 *      - Lapisan aplikasi tidak boleh tahu soal HTTP maupun persistence.
 */
class ArchitectureLayerTest {

    private static final Path SOURCE_ROOT = Path.of("src", "main", "java", "id", "osprey", "iamservice");

    @Test
    void domainMustNotDependOnFramework() throws IOException {
        List<String> violations = scan("domain",
                importPattern -> importPattern.startsWith("org.springframework.")
                        || importPattern.startsWith("jakarta.")
                        || importPattern.startsWith("io.swagger.")
                        || importPattern.startsWith("com.fasterxml."));

        assertTrue(violations.isEmpty(),
                "Paket domain tidak boleh mengimpor framework. Pelanggaran:\n" + String.join("\n", violations));
    }

    @Test
    void applicationMustNotDependOnWebOrPersistence() throws IOException {
        List<String> violations = scan("application",
                importPattern -> importPattern.startsWith("org.springframework.web.")
                        || importPattern.startsWith("org.springframework.security.")
                        || importPattern.startsWith("org.springframework.data.jpa.")
                        || importPattern.startsWith("jakarta.persistence")
                        || importPattern.startsWith("jakarta.servlet")
                        || importPattern.startsWith("jakarta.validation")
                        || importPattern.startsWith("io.swagger."));

        assertTrue(violations.isEmpty(),
                "Paket application tidak boleh mengimpor web atau persistence. Pelanggaran:\n"
                        + String.join("\n", violations));
    }

    /**
     * Memindai seluruh berkas java pada satu paket dan mengumpulkan import
     * yang melanggar aturan.
     */
    private List<String> scan(String packageName, java.util.function.Predicate<String> isForbidden)
            throws IOException {

        Path packageRoot = SOURCE_ROOT.resolve(packageName);
        List<String> violations = new ArrayList<>();

        if (!Files.isDirectory(packageRoot)) {
            return violations;
        }

        try (Stream<Path> files = Files.walk(packageRoot)) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                for (String importPattern : readImports(path)) {
                    if (isForbidden.test(importPattern)) {
                        violations.add(packageName + "/" + path.getFileName() + " -> " + importPattern);
                    }
                }
            });
        }

        return violations;
    }

    /**
     * Mengambil daftar import dari satu berkas java.
     */
    private List<String> readImports(Path path) {
        List<String> imports = new ArrayList<>();

        try {
            for (String line : Files.readAllLines(path)) {
                String trimmed = line.trim();

                if (trimmed.startsWith("import ") && !trimmed.startsWith("import static ")) {
                    imports.add(trimmed.substring("import ".length()).replace(";", "").trim());
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Gagal membaca " + path, ex);
        }

        return imports;
    }
}
