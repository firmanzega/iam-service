package id.osprey.iamservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Deskripsi OpenAPI untuk dokumentasi endpoint.
 *
 * Kenapa skema keamanan didefinisikan di sini:
 *      - Halaman Swagger membaca skema ini untuk menampilkan tombol
 *        "Authorize". Tanpa itu, pengguna tidak bisa mencoba endpoint yang
 *        butuh token.
 *      - Skema "bearer" ditulis eksplisit, bukan relied on pada heuristik
 *        springdoc, supaya dokumentasi tidak pernah berbeda dengan
 *        perilaku nyata layanan.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER_SCHEME = "bearerAuth";

    @Bean
    OpenAPI iamServiceOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("IAM Service API")
                        .version("1.0.0")
                        .description("""
                                Layanan pengelolaan identitas dan akses (Identity and Access Management).

                                Semua endpoint kecuali login dan refresh token memerlukan token akses
                                pada header Authorization dengan format "Bearer <token>".
                                """)
                        .contact(new Contact().name("Tim IAM"))
                        .license(new License().name("Proprietary")))
                .components(new Components()
                        .addSecuritySchemes(BEARER_SCHEME, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token akses hasil dari POST /auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
