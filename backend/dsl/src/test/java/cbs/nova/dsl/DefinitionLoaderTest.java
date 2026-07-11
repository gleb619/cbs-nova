package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import cbs.nova.dsl.config.ContextFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class DefinitionLoaderTest {

  private final ContextFactory contextFactory = new ContextFactory();
  @TempDir
  Path tempDir;

  @BeforeEach
  void reset() {
    GlobalManager.getInstance().resetForTests();
  }

  @Test
  void loadsProcessFromSpiProvider() {
    var gm = GlobalManager.getInstance();
    new DefinitionLoader().load(tempDir, gm);

    var ctx = contextFactory.of("test", ExecutionMode.PREVIEW);
    var result = gm.runProcess("SpiLoadedProcess", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("spi-loaded");
  }

  @Test
  void loadIgnoresSourceDirectoryArgument() {
    var gm = GlobalManager.getInstance();
    assertThatCode(() -> new DefinitionLoader().load(tempDir, gm)).doesNotThrowAnyException();
    assertThat(gm.hasProcess("SpiLoadedProcess")).isTrue();
  }
}
