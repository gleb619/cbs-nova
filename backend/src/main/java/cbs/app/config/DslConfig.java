package cbs.app.config;

import cbs.dsl.runtime.DslRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DslConfig {

  @Bean
  public DslRegistry dslRegistry() {
    return new DslRegistry();
  }
}
