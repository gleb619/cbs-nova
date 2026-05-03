package cbs;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test configuration that provides a Testcontainers-backed PostgreSQL for integration tests.
 *
 * <p>Usage: {@code SpringApplication.from(CbsApp::main).with(TestApp.class).run(args)}
 */
@TestConfiguration
public class TestApp {

  @Bean(destroyMethod = "stop")
  PostgreSQLContainer<?> postgreSQLContainer() {
    return new PostgreSQLContainer<>(DockerImageName.parse("postgres:17-alpine"));
  }
}
