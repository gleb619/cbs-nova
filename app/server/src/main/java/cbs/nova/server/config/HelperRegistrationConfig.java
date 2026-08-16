package cbs.nova.server.config;

import cbs.nova.dsl.GlobalManager;
import cbs.nova.server.helpers.GreeterHelper;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HelperRegistrationConfig {

  @Bean
  ApplicationRunner registerGreeterHelper(GreeterHelper greeterHelper) {
    return (ApplicationArguments args) ->
        GlobalManager.globalManager().registerHelper("greeter", greeterHelper);
  }
}
