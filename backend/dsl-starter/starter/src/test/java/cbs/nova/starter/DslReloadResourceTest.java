package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.CompilingDslDefinitionLoader;
import cbs.nova.dsl.DslDefinitionLoader;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.ServiceLoaderDslDefinitionLoader;
import cbs.nova.starter.config.DslReloadRouterConfiguration;
import cbs.nova.starter.config.properties.DslProperties;
import cbs.nova.starter.controllers.DslReloadHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

class DslReloadResourceTest {

  private DslReloadHandler resource;
  private final DslDefinitionLoader loader = new CompilingDslDefinitionLoader(
          new ServiceLoaderDslDefinitionLoader());

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    resource = new DslReloadHandler(new DslProperties(null, null, null, null), loader);
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  private void setSourceDir(String value) {
    resource = new DslReloadHandler(new DslProperties(value, null, null, null), loader);
  }

  private static ServerRequest reloadRequest() {
    return ServerRequest.create(
            new MockHttpServletRequest("POST", "/api/dsl/reload"), List.of());
  }

  @Test
  void reloadReturns409WhenSourceDirBlank() throws Exception {
    setSourceDir("");
    ServerResponse response = resource.reload(reloadRequest());
    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void reloadReturns409WhenSourceDirNotFound() throws Exception {
    setSourceDir("/tmp/cbs-nova-does-not-exist-" + System.nanoTime());
    ServerResponse response = resource.reload(reloadRequest());
    assertThat(response.statusCode().value()).isEqualTo(409);
  }

  @Test
  void routerFunctionIsRegisteredByDefault() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslPropertiesConfiguration.class,
                    DslReloadRouterConfiguration.class, DslReloadHandler.class)
            .run(ctx -> assertThat(ctx).hasSingleBean(RouterFunction.class));
  }

  @Test
  void routerFunctionSkippedWhenDisabled() {
    new ApplicationContextRunner()
            .withUserConfiguration(DslPropertiesConfiguration.class,
                    DslReloadRouterConfiguration.class, DslReloadHandler.class)
            .withPropertyValues("dsl.reload.enabled=false")
            .run(ctx -> assertThat(ctx).doesNotHaveBean(RouterFunction.class));
  }

  @Test
  void reloadLoadsDefinitionsViaSpiAndNewClassLoader() throws Exception {
    Path sourceDir = createTemporaryDslSourceDir();
    try {
      setSourceDir(sourceDir.toString());
      ServerResponse response = resource.reload(reloadRequest());
      assertThat(response.statusCode().value()).isEqualTo(204);
      assertThat(GlobalManager.globalManager().hasProcess("ReloadTestProcess")).isTrue();
    } finally {
      deleteRecursively(sourceDir);
    }
  }

  private Path createTemporaryDslSourceDir() throws IOException {
    Path sourceDir = Files.createTempDirectory("dsl-reload-test-");
    Path services = sourceDir.resolve("META-INF/services");
    Files.createDirectories(services);
    Files.writeString(services.resolve("cbs.nova.dsl.DslDefinitionProvider"),
            "ReloadTestProvider\n");
    Files.writeString(sourceDir.resolve("ReloadTestProvider.java"), """
            import cbs.nova.dsl.Dsl;
            import cbs.nova.dsl.DslDefinitionProvider;
            import cbs.nova.dsl.DslObject;
            import cbs.nova.dsl.Result;
            import java.util.List;

            public class ReloadTestProvider implements DslDefinitionProvider {
              @Override
              public List<DslObject> definitions() {
                return List.of(
                    Dsl.process("ReloadTestProcess").execute(ctx -> Result.success("ok")).build());
              }
            }
            """);
    return sourceDir;
  }

  private void deleteRecursively(Path path) throws IOException {
    try (Stream<Path> stream = Files.walk(path)) {
      stream.sorted((a, b) -> -a.compareTo(b)).forEach(p -> {
        try {
          Files.deleteIfExists(p);
        } catch (IOException e) {
          // ignore
        }
      });
    }
  }

  @Configuration
  @EnableConfigurationProperties(DslProperties.class)
  static class DslPropertiesConfiguration {

    @Bean
    DslDefinitionLoader dslDefinitionLoader() {
      return new CompilingDslDefinitionLoader(new ServiceLoaderDslDefinitionLoader());
    }
  }
}
