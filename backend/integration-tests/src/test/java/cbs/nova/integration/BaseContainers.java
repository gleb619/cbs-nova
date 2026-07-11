package cbs.nova.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
public abstract class BaseContainers {

  private static String temporalDbSeeds() {
    var env = System.getenv("TEMPORAL_DB_SEEDS");
    if (env != null && !env.isBlank()) {
      return env;
    }
    try {
      var proc = new ProcessBuilder("docker", "network", "inspect", "bridge", "--format",
              "{{(index .IPAM.Config 0).Gateway}}").redirectErrorStream(true).start();
      var bytes = proc.getInputStream().readAllBytes();
      proc.waitFor();
      var gw = new String(bytes).trim();
      if (!gw.isEmpty()) {
        return gw;
      }
    } catch (Exception ignored) {
    }
    return "host.docker.internal";
  }

  protected static final GenericContainer<?> TEMPORAL = new GenericContainer<>(
          DockerImageName.parse("temporalio/auto-setup:1.25.2"))
          .withExposedPorts(7233)
          .withEnv("DB", System.getenv().getOrDefault("TEMPORAL_DB", "mysql8"))
          .withEnv("MYSQL_SEEDS", temporalDbSeeds())
          .withEnv("MYSQL_USER", System.getenv().getOrDefault("TEMPORAL_DB_USER", "root"))
          .withEnv("MYSQL_PWD", System.getenv().getOrDefault("TEMPORAL_DB_PWD", "root"))
          .withEnv("DB_PORT", System.getenv().getOrDefault("TEMPORAL_DB_PORT", "3306"))
          .withEnv("BIND_ON_IP", "0.0.0.0")
          .withEnv("TEMPORAL_BROADCAST_ADDRESS", "127.0.0.1")
          .waitingFor(Wait.forListeningPort().withStartupTimeout(java.time.Duration.ofMinutes(3)));

  protected static final GenericContainer<?> KEYCLOAK = new GenericContainer<>(
          DockerImageName.parse("quay.io/keycloak/keycloak:22.0.0"))
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
            () -> "http://" + KEYCLOAK.getHost() + ":" + KEYCLOAK.getMappedPort(8080)
                    + "/realms/cbs-nova");
  }
}
