package id.osprey.iamservice.domain.shared;

/**
 * Arah pengurutan untuk satu properti.
 *
 * @param property nama properti yang diurutkan
 * @param descending true untuk urutan menurun
 */
public record SortSpec(String property, boolean descending) {
}
