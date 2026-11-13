# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- tahap build
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

# Berkas pembungkus dan pom disalin lebih dulu supaya unduhan dependensi
# tersimpan sebagai lapisan cache. Selama pom tidak berubah, build berikutnya
# tidak perlu mengunduh ulang seluruh dependensi.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress -DskipTests dependency:go-offline

# Sumber dan konfigurasi analisis statis disalin setelah dependensi siap.
# Konfigurasi ikut disalin karena pom merujuk config/checkstyle/checkstyle.xml;
# tanpa berkas itu, plugin gagal saat dijalankan.
COPY config/ config/
COPY src/ src/
RUN ./mvnw --batch-mode --no-transfer-progress -DskipTests package

# ---------------------------------------------------------------- tahap jalan
FROM eclipse-temurin:21-jre

WORKDIR /app

# Aplikasi berjalan sebagai pengguna tanpa hak istimewa. Nomor dipakai langsung
# supaya image tidak bergantung pada perkakas pembuatan akun yang belum tentu
# ada di semua varian image dasar.
USER 10001:10001

COPY --from=build /app/target/*.jar app.jar

EXPOSE 9090

# Profil produksi dipilih di sini agar menjalankan image tanpa argumen tambahan
# tidak pernah memakai profil pengembangan beserta akun admin bawaannya.
ENV SPRING_PROFILES_ACTIVE=prod

# JAVA_TOOL_OPTIONS dibaca langsung oleh JVM, sehingga tidak perlu shell untuk
# meneruskan nilai dari environment. Shell akan menghalangi sinyal berhenti
# sampai ke proses Java.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
