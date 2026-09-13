package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.DslProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(DslProperties.class)
public class ApiKeyAuthMisconfigurationWarning {

  @Bean
  public SmartInitializingSingleton apiKeyAuthMisconfigurationCheck(DslProperties dslProperties) {
    return () -> {
      var auth = dslProperties.auth();
      if (auth.apiKey() != null && !auth.apiKey().isBlank() && !auth.enabled()) {
        log.warn("cbs.dsl.auth.api-key is configured but cbs.dsl.auth.enabled=false — "
                + "ApiKeyAuthFilter beans are NOT registered and every /api/* request flows through "
                + "unauthenticated. Set cbs.dsl.auth.enabled=true to activate the API-key guard.");
      }
    };
  }
}
