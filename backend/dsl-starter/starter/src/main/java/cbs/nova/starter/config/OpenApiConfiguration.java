package cbs.nova.starter.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Optional;

@Configuration
public class OpenApiConfiguration {

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
