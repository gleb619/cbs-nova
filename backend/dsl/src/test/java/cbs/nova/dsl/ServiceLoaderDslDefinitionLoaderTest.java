package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cbs.nova.dsl.config.ContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;

class ServiceLoaderDslDefinitionLoaderTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @BeforeEach
  void reset() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void loadDiscoversProvidersFromContextClassLoader() {
    var gm = GlobalManager.globalManager();

    new ServiceLoaderDslDefinitionLoader().load(gm);

    assertThat(gm.hasProcess("SpiLoadedProcess")).isTrue();
    var ctx = contextFactory.of("test", ExecutionMode.PREVIEW);
    var result = gm.runProcess("SpiLoadedProcess", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("spi-loaded");
  }

  @Test
  void loadDiscoversProvidersFromExplicitClassLoader() throws Exception {
    var gm = GlobalManager.globalManager();
    var parent = Thread.currentThread().getContextClassLoader();
    var classLoader = new URLClassLoader(new URL[0], parent);

    assertThatCode(() -> new ServiceLoaderDslDefinitionLoader().load(classLoader, gm))
            .doesNotThrowAnyException();

    assertThat(gm.hasProcess("SpiLoadedProcess")).isTrue();
    var ctx = contextFactory.of("test", ExecutionMode.PREVIEW);
    var result = gm.runProcess("SpiLoadedProcess", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("spi-loaded");
  }
}
