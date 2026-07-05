package cbs.nova.starter;

import cbs.nova.dsl.PropertyResolver;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.MapPropertySource;

import java.util.HashMap;

@AutoConfiguration
public class PropertyResolverConfiguration {

  @Bean
  @ConditionalOnMissingBean
  PropertyResolver dslPropertyResolver(Environment environment) {
    var props = new HashMap<String, String>();
    if (environment instanceof ConfigurableEnvironment ce) {
      for (var source : ce.getPropertySources()) {
        if (source instanceof MapPropertySource mps) {
          for (var key : mps.getPropertyNames()) {
            var value = environment.getProperty(key);
            if (value != null) {
              props.put(key, value);
            }
          }
        }
      }
    }
    return new PropertyResolver(props, false);
  }
}
