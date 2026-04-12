package cbs.app.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI customOpenAPI(
      @Value("${keycloak.auth-server-url:http://localhost:9080}") String keycloakServerUrl,
      @Value("${keycloak.realm:cbsnova}") String keycloakRealm) {
    String securitySchemeName = "bearerAuth";
    String bearerAuthUrl =
        "%s/realms/%s/protocol/openid-connect/token".formatted(keycloakServerUrl, keycloakRealm);

    return new OpenAPI()
        .info(new Info()
            .title("CBS Nova API")
            .description(
                "CBS Nova Backend API for settings management with Keycloak authentication. "
                + "MassOperations: Mass operation execution API for batch processing.")
            .version("1.0.0")
            .contact(new Contact().name("CBS Nova Team").email("team@cbs-nova.com"))
            .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
        .servers(List.of(
            new Server().url("http://localhost:7070").description("Development server"),
            new Server().url("http://localhost:3000").description("Development admin-ui")))
        .tags(List.of(
            new Tag().name("MassOperations").description("Mass operation execution API"),
            new Tag().name("Events").description("Business event execution API")))
        .addSecurityItem(new SecurityRequirement().addList("%sJwt".formatted(securitySchemeName)))
        .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
        .components(new Components()
            .addSecuritySchemes(
                "%sJwt".formatted(securitySchemeName),
                new SecurityScheme()
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .in(SecurityScheme.In.HEADER)
                    .name("Authorization")
                    .description("JWT Bearer token from Local Auth"))
            .addSecuritySchemes(
                securitySchemeName,
                new SecurityScheme()
                    .type(SecurityScheme.Type.OAUTH2)
                    .flows(new OAuthFlows()
                        .password(new OAuthFlow()
                            .tokenUrl(bearerAuthUrl)
                            .refreshUrl(bearerAuthUrl)
                            .scopes(new Scopes().addString("openid", "OpenID Connect"))))));
  }
}
