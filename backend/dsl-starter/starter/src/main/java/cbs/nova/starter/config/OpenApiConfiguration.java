package cbs.nova.starter.config;

import cbs.nova.starter.core.StarterConstants;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class OpenApiConfiguration {

  private static final String DEFAULT_VERSION = StarterConstants.OPENAPI_DEFAULT_VERSION;

  @Bean
  public OpenAPI dslOpenApi(Optional<BuildProperties> buildProperties) {
    return new OpenAPI().info(new Info()
            .title("cbs-nova DSL API")
            .description("REST contract for the cbs-nova DSL runtime: introspection, "
                    + "preview / run / explain, and reload of DSL definitions.")
            .version(buildProperties.map(BuildProperties::getVersion).orElse(DEFAULT_VERSION)));
  }
}
