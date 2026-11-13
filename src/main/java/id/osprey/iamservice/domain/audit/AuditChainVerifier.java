package id.osprey.iamservice.domain.audit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Memeriksa keutuhan rantai hash jejak audit.
 *
 * Kenapa pemeriksaan memakai keterkaitan hash, bukan urutan id:
 *      - Hash dihitung saat insert di dalam kunci transaksi, tetapi id sudah
 *        terlanjur dibuat sebelum kunci diperoleh. Pada penulisan yang benar
 *        benar bersamaan, urutan rantai bisa berbeda dari urutan id.
 *      - Yang menentukan keutuhan adalah setiap entri menunjuk ke hash entri
 *        lain yang benar-benar ada, tepat satu entri menjadi kepala, dan
 *        seluruh entri bisa dicapai dari kepala. Urutan id tidak dipakai.
 *
 * Kode alasan yang dikembalikan:
 *      - HASH_MISMATCH: isi entri tidak cocok dengan hash yang tersimpan.
 *      - MISSING_PREDECESSOR: entri menunjuk hash yang tidak ada di database.
 *      - MULTIPLE_HEADS: ada lebih dari satu entri tanpa pendahulu.
 *      - UNREACHABLE_ENTRY: ada entri yang tidak bisa dicapai dari kepala,
 *        biasanya karena siklus atau pemutusan rantai.
 */
public final class AuditChainVerifier {

    /** Isi entri berubah setelah hash dihitung. */
    public static final String HASH_MISMATCH = "HASH_MISMATCH";

    /** Pendahulu yang ditunjuk tidak ada, biasanya karena baris dihapus. */
    public static final String MISSING_PREDECESSOR = "MISSING_PREDECESSOR";

    /** Lebih dari satu entri mengaku sebagai awal rantai. */
    public static final String MULTIPLE_HEADS = "MULTIPLE_HEADS";

    /** Entri tidak bisa dicapai dari awal rantai. */
    public static final String UNREACHABLE_ENTRY = "UNREACHABLE_ENTRY";

    private AuditChainVerifier() {
    }

    /**
     * Memeriksa seluruh entri dan mengembalikan kerusakan pertama menurut id.
     */
    public static AuditChainVerification verify(List<AuditChainEntry> entries) {
        if (entries.isEmpty()) {
            return new AuditChainVerification(true, 0, null, null);
        }

        List<AuditChainEntry> ordered = new ArrayList<>(entries);
        ordered.sort(Comparator.comparing(AuditChainEntry::id));

        Map<String, AuditChainEntry> byHash = new HashMap<>();
        for (AuditChainEntry entry : ordered) {
            byHash.putIfAbsent(entry.entryHash(), entry);
        }

        // LinkedHashMap menjaga urutan id, sehingga kerusakan pertama yang
        // dilaporkan selalu sama untuk data yang sama.
        Map<Long, String> violations = new LinkedHashMap<>();

        for (AuditChainEntry entry : ordered) {
            if (!hashOf(entry).equals(entry.entryHash())) {
                violations.putIfAbsent(entry.id(), HASH_MISMATCH);
            }

            if (entry.prevHash() != null && !byHash.containsKey(entry.prevHash())) {
                violations.putIfAbsent(entry.id(), MISSING_PREDECESSOR);
            }
        }

        List<AuditChainEntry> heads = ordered.stream()
                .filter(entry -> entry.prevHash() == null)
                .toList();

        if (heads.size() > 1) {
            for (int index = 1; index < heads.size(); index++) {
                violations.putIfAbsent(heads.get(index).id(), MULTIPLE_HEADS);
            }
        }

        if (heads.isEmpty()) {
            violations.putIfAbsent(ordered.get(0).id(), UNREACHABLE_ENTRY);
        } else {
            markUnreachableEntries(ordered, heads.get(0), violations);
        }

        if (violations.isEmpty()) {
            return new AuditChainVerification(true, ordered.size(), null, null);
        }

        Map.Entry<Long, String> first = violations.entrySet().iterator().next();
        return new AuditChainVerification(false, ordered.size(), first.getKey(), first.getValue());
    }

    /**
     * Menelusuri rantai dari kepala. Entri yang tidak pernah dikunjungi
     * menandakan siklus atau cabang yang terputus.
     */
    private static void markUnreachableEntries(List<AuditChainEntry> ordered,
                                               AuditChainEntry head,
                                               Map<Long, String> violations) {
        Map<String, AuditChainEntry> successor = new HashMap<>();
        for (AuditChainEntry entry : ordered) {
            if (entry.prevHash() != null) {
                successor.putIfAbsent(entry.prevHash(), entry);
            }
        }

        Set<Long> visited = new HashSet<>();
        AuditChainEntry current = head;
        while (current != null && visited.add(current.id())) {
            current = successor.get(current.entryHash());
        }

        for (AuditChainEntry entry : ordered) {
            if (!visited.contains(entry.id())) {
                violations.putIfAbsent(entry.id(), UNREACHABLE_ENTRY);
            }
        }
    }

    /**
     * Menghitung hash satu entri dengan bentuk data yang sama persis dengan
     * fungsi audit_log_hash di migrasi V7.
     */
    static String hashOf(AuditChainEntry entry) {
        String payload = (entry.prevHash() == null ? "" : entry.prevHash())
                + "|" + entry.id()
                + "|" + (entry.actorUserId() == null ? "" : entry.actorUserId())
                + "|" + entry.action()
                + "|" + (entry.targetUserId() == null ? "" : entry.targetUserId())
                + "|" + epochMicros(entry.createdAt())
                + "|" + (entry.details() == null ? "" : entry.details());

        return sha256Hex(payload);
    }

    private static long epochMicros(Instant instant) {
        return instant.getEpochSecond() * 1_000_000L + instant.getNano() / 1_000L;
    }

    private static String sha256Hex(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(payload.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 tidak tersedia", ex);
        }
    }
}
