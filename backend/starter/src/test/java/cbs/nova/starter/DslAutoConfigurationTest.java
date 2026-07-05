package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
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

  @Test
  void registersHelperAnnotatedClassesFromScanPackages() throws Exception {
    GlobalManager.resetForTests();
    var config = new DslAutoConfiguration();
    setField(config, "helperScanPackages", "cbs.nova.starter");

    config.loadDslDefinitions();

    assertThat(GlobalManager.getInstance().hasHelper("test-helper-fixture")).isTrue();
  }

  @Test
  void doesNothingWhenHelperScanPackagesBlank() {
    var config = new DslAutoConfiguration();
    config.loadDslDefinitions();

    assertThat(GlobalManager.getInstance().hasHelper("test-helper-fixture")).isFalse();
  }

  @Helper(name = "test-helper-fixture")
  static final class FixtureHelper implements Executable<String, String> {
    @Override
    public Result<String> execute(Context<String> ctx) {
      return Result.success(ctx.body());
    }
  }

  private void setField(Object target, String fieldName, String value) throws Exception {
    var field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }
}
