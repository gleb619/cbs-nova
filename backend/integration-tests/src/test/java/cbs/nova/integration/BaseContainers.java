package cbs.nova.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class BaseContainers {

  protected static final GenericContainer<?> TEMPORAL =
      new GenericContainer<>(DockerImageName.parse("temporalio/auto-setup:1.25.2"))
          .withExposedPorts(7233)
          .withEnv("DB", "sqlite")
          .waitingFor(Wait.forLogMessage(".*Temporal server started.*", 1));

  protected static final GenericContainer<?> KEYCLOAK =
      new GenericContainer<>(DockerImageName.parse("quay.io/keycloak/keycloak:22.0.0"))
          .withExposedPorts(8080)
          .withEnv("KEYCLOAK_ADMIN", "admin")
          .withEnv("KEYCLOAK_ADMIN_PASSWORD", "admin")
          .withEnv("KC_HOSTNAME", "localhost")
          .withEnv("KC_HOSTNAME_STRICT", "false")
          .withEnv("KC_HOSTNAME_STRICT_HTTPS", "false")
          .withCommand("start-dev")
          .waitingFor(Wait.forHttp("/realms/master").forPort(8080));

  static {
    TEMPORAL.start();
    KEYCLOAK.start();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    registry.add(
        "temporal.target",
        () -> TEMPORAL.getHost() + ":" + TEMPORAL.getMappedPort(7233));
    registry.add(
        "spring.security.oauth2.resourceserver.jwt.issuer-uri",
        () -> "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080) + "/realms/cbs-nova");
  }
}
