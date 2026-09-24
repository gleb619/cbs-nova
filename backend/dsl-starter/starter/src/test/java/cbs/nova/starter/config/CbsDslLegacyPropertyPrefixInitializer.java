package cbs.nova.starter.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Test-only {@link ApplicationContextInitializer} that applies the production legacy-prefix
 * migration so {@link org.springframework.boot.test.context.runner.ApplicationContextRunner} tests
 * honour the deprecated {@code csb.dsl.*} keys.
 *
 * <p>
 * {@code ApplicationContextRunner} does not run {@code EnvironmentPostProcessor} imports, so tests
 * that need to verify backwards compatibility must include this initializer explicitly.
 */
public class CbsDslLegacyPropertyPrefixInitializer
        implements
          ApplicationContextInitializer<ConfigurableApplicationContext> {

  @Override
  public void initialize(ConfigurableApplicationContext applicationContext) {
    new CbsDslLegacyPropertyPrefixPostProcessor()
            .postProcessEnvironment(applicationContext.getEnvironment(), null);
  }
}
