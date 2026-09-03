package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cbs.nova.dsl.config.ContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.net.URLClassLoader;

class DefinitionLoaderTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @BeforeEach
  void reset() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void loadDiscoversProvidersFromContextClassLoader() {
    var gm = GlobalManager.globalManager();

    new DefinitionLoader().load(gm);

    assertThat(gm.hasProcess("SpiLoadedProcess")).isTrue();
    var ctx = contextFactory.of("test", ExecutionMode.PREVIEW);
    var result = gm.runProcess("SpiLoadedProcess", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("spi-loaded");
  }

  @Test
  void loadReturnsDrilldownWithCountsAndNamesPerType() {
    var gm = GlobalManager.globalManager();

    var load = new DefinitionLoader().load(gm);

    assertThat(load.total()).isEqualTo(1);
    assertThat(load.processCount()).isEqualTo(1);
    assertThat(load.transactionCount()).isZero();
    assertThat(load.functionCount()).isZero();
    assertThat(load.processes()).containsExactly("SpiLoadedProcess");
    assertThat(load.transactions()).isEmpty();
    assertThat(load.functions()).isEmpty();
  }

  @Test
  void loadDiscoversProvidersFromExplicitClassLoader() throws Exception {
    var gm = GlobalManager.globalManager();
    var parent = Thread.currentThread().getContextClassLoader();
    var classLoader = new URLClassLoader(new URL[0], parent);

    var load = new DefinitionLoader().load(classLoader, gm);
    assertThat(load.total()).isEqualTo(1);
    assertThat(load.processes()).containsExactly("SpiLoadedProcess");

    assertThat(gm.hasProcess("SpiLoadedProcess")).isTrue();
    var ctx = contextFactory.of("test", ExecutionMode.PREVIEW);
    var result = gm.runProcess("SpiLoadedProcess", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("spi-loaded");
  }
}
