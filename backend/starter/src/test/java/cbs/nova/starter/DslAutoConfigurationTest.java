package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.GlobalManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class DslAutoConfigurationTest {

  @TempDir
  Path tempDir;

  @AfterEach
  void resetGlobalManager() {
    GlobalManager.resetForTests();
  }

  @Test
  void doesNothingWhenSourceDirBlank() {
    var config = new DslAutoConfiguration();
    config.loadDslDefinitions();
  }

  @Test
  void throwsWhenSourceDirDoesNotExist() throws Exception {
    var config = new DslAutoConfiguration();
    setField(config, "sourceDirProperty", "/nonexistent/path/to/dsl-sources");
    assertThatThrownBy(config::loadDslDefinitions)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("dsl.source-dir does not exist");
  }

  @Test
  void loadsEmptyDirWithoutError() throws Exception {
    var config = new DslAutoConfiguration();
    setField(config, "sourceDirProperty", tempDir.toString());
    config.loadDslDefinitions();
  }

  private void setField(Object target, String fieldName, String value) throws Exception {
    var field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
