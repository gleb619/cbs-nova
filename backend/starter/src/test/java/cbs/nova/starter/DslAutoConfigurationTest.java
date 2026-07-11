package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Helper;
import cbs.nova.dsl.Result;
import cbs.nova.starter.config.DslAutoConfiguration;
import cbs.nova.starter.listeners.ExternalCallListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

class DslAutoConfigurationTest {

  @TempDir
  Path tempDir;

  @AfterEach
  void resetGlobalManager() {
    GlobalManager.getInstance().resetForTests();
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
    GlobalManager.getInstance().resetForTests();
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

  @Test
  void listenersRegisteredWithTracker() throws Exception {
    var tracker = new ExternalCallTracker();
    var callCount = new AtomicInteger(0);
    ExternalCallListener listener = (type, target, op, payload) -> callCount.incrementAndGet();

    var config = new DslAutoConfiguration();
    setListeners(config, List.of(listener));
    setTracker(config, tracker);
    config.loadDslDefinitions();

    tracker.record("http", "svc", "GET", null);
    assertThat(callCount.get()).isEqualTo(1);
  }

  @Test
  void noErrorWhenNoListenerBeansPresent() throws Exception {
    var config = new DslAutoConfiguration();
    setTracker(config, new ExternalCallTracker());
    config.loadDslDefinitions();
  }

  @Test
  void listenersSkippedWhenTrackerNull() throws Exception {
    var callCount = new AtomicInteger(0);
    ExternalCallListener listener = (type, target, op, payload) -> callCount.incrementAndGet();

    var config = new DslAutoConfiguration();
    setListeners(config, List.of(listener));
    config.loadDslDefinitions();

    assertThat(callCount.get()).isEqualTo(0);
  }

  private void setField(Object target, String fieldName, String value) throws Exception {
    var field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private void setListeners(DslAutoConfiguration config, List<ExternalCallListener> listeners)
          throws Exception {
    Field field = DslAutoConfiguration.class.getDeclaredField("externalCallListeners");
    field.setAccessible(true);
    field.set(config, listeners);
  }

  private void setTracker(DslAutoConfiguration config, ExternalCallTracker tracker)
          throws Exception {
    Field field = DslAutoConfiguration.class.getDeclaredField("externalCallTracker");
    field.setAccessible(true);
    field.set(config, tracker);
  }
}
