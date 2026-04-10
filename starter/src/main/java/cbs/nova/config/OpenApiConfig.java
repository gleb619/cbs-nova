package cbs.nova.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Value("${keycloak.auth-server-url:http://localhost:9080}")
  private String keycloakServerUrl;

  @Value("${keycloak.realm:cbsnova}")
  private String keycloakRealm;

  @Bean
  public OpenAPI customOpenAPI() {
    final String securitySchemeName = "bearerAuth";
    final String bearerAuthUrl =
        keycloakServerUrl + "/realms/" + keycloakRealm + "/protocol/openid-connect/token";

    return new OpenAPI()
        .info(new Info()
            .title("CBS Nova API")
            .description(
                "CBS Nova Backend API for settings management with Keycloak authentication")
            .version("1.0.0")
            .contact(new Contact().name("CBS Nova Team").email("team@cbs-nova.com"))
            .license(new License()
                .name("Apache 2.0")
                .url("https://www.apache.org/licenses/LICENSE-2.0")))
        .servers(List.of(
            new Server().url("http://localhost:7070").description("Development server"),
            new Server().url("https://api.cbs-nova.com").description("Production server")))
        .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
        .components(new Components()
            .addSecuritySchemes(
                securitySchemeName,
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .in(SecurityScheme.In.HEADER)
                    .name("Authorization")
                    .description("JWT Bearer token from Keycloak")));
  }
}
