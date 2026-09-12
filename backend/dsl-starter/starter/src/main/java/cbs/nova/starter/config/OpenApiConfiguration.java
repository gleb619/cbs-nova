package cbs.nova.starter.config;

import static cbs.nova.starter.core.StarterConstants.OPENAPI_DEFAULT_VERSION;

import cbs.nova.starter.core.StarterConstants;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class OpenApiConfiguration {

  @Bean
  public OpenAPI dslOpenApi(Optional<BuildProperties> buildProperties) {
    return new OpenAPI().info(new Info()
            .title("cbs-nova DSL API")
            .description("REST contract for the cbs-nova DSL runtime: introspection, "
                    + "preview / run / explain, and reload of DSL definitions.")
            .version(buildProperties.map(BuildProperties::getVersion)
                    .orElse(OPENAPI_DEFAULT_VERSION)));
  }
}
