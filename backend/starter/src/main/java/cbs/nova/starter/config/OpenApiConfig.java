package cbs.nova.starter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

/**
 * Provides the global {@link OpenAPI} metadata for the cbs-nova DSL REST contract. Without this
 * bean, the spec at {@code /v3/api-docs} ships with a blank title and version. The DSL controllers
 * ({@code DslIntrospectionResource}, {@code DslRuntimeResource}, {@code DslReloadResource}) carry
 * the per-operation {@code @Operation}/{@code @Tag} annotations — this bean only fills the
 * top-level info block.
 *
 * <p>
 * The version is sourced from {@link BuildProperties} when present (produced by the
 * {@code springBoot { buildInfo() }} block in {@code backend/starter/build.gradle}). When
 * {@link BuildProperties} is not on the classpath (e.g. in IDE-driven tests without the full Gradle
 * task graph) the bean falls back to {@code 0.0.1-SNAPSHOT}.
 */
@Configuration
public class OpenApiConfig {

  // TODO: add to build.gradle a git plugin, return here a version
  private static final String DEFAULT_VERSION = "0.0.1-SNAPSHOT";

  @Bean
  public OpenAPI dslOpenApi(Optional<BuildProperties> buildProperties) {
    return new OpenAPI().info(new Info()
            .title("cbs-nova DSL API")
            .description("REST contract for the cbs-nova DSL runtime: introspection, "
                    + "preview / run / explain, and reload of DSL definitions.")
            .version(buildProperties.map(BuildProperties::getVersion).orElse(DEFAULT_VERSION)));
  }
}
