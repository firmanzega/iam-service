package id.osprey.iamservice.domain.tenant;

/**
 * Penyimpan tenantId yang berlaku untuk satu permintaan (request-scoped).
 * Digunakan oleh lapisan infrastruktur untuk menerapkan filter tenant_id
 * pada setiap query ke tabel inti.
 *
 * Menggunakan ThreadLocal karena setiap permintaan HTTP diproses dalam satu
 * thread, dan tenantId perlu tersedia di seluruh alur pemanggilan tanpa
 * harus ditransmisikan sebagai parameter manual ke setiap method.
 */
public final class TenantContext {

    private static final InheritableThreadLocal<Long> current = new InheritableThreadLocal<>();

    private TenantContext() {
    }

    public static void setTenantId(Long tenantId) {
        current.set(tenantId);
    }

    public static Long getTenantId() {
        return current.get();
    }

    public static void clear() {
        current.remove();
    }
}
