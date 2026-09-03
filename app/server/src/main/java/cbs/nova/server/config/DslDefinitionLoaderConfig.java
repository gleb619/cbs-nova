package cbs.nova.server.config;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.DefinitionLoader;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DslDefinitionLoaderConfig {

  @Bean
  ApplicationRunner loadDslDefinitions() {
    return (ApplicationArguments args) ->
        new DefinitionLoader().load(GlobalManager.globalManager());
  }
}
