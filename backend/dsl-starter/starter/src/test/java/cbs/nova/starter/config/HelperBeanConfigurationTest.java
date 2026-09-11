package cbs.nova.starter.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.starter.annotation.HelperBean;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties.Level;
import java.net.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.context.annotation.Configuration;

/**
 * Verifies that {@link HelperBean} factory methods create real Spring beans and are automatically
 * registered as DSL helpers in {@link GlobalManager}.
 */
class HelperBeanConfigurationTest {

  private static final TestHelper SHARED_HELPER = new TestHelper();

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(SpringHelperConfiguration.class, TestHelperConfiguration.class);

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void helperBeanIsRegisteredAsSpringBeanAndDslHelper() {
    runner.run(ctx -> {
      assertThat(ctx).hasSingleBean(TestHelper.class);

      var globalManager = GlobalManager.globalManager();
      assertThat(globalManager.hasHelper("testHelper")).isTrue();

      var previewCtx = globalManager.createContext(
              "Nova", Map.of(), ExecutionMode.PREVIEW, "helper-bean-test");
      Result<?> result = globalManager.runHelper("testHelper", previewCtx);
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.value()).isEqualTo("Hello, Nova!");
    });
  }

  @Test
  void helperBeanAlreadyRegisteredWithSameInstanceIsIdempotent() {
    GlobalManager.globalManager().registerHelper("sharedTestHelper", SHARED_HELPER);

    runner.withUserConfiguration(SameInstanceHelperConfiguration.class).run(ctx -> {
      assertThat(GlobalManager.globalManager().hasHelper("sharedTestHelper")).isTrue();

      var previewCtx = GlobalManager.globalManager().createContext(
              "Nova", Map.of(), ExecutionMode.PREVIEW, "helper-bean-same-instance-test");
      Result<?> result = GlobalManager.globalManager().runHelper("sharedTestHelper", previewCtx);
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.value()).isEqualTo("Hello, Nova!");
    });
  }

  @Test
  void duplicateHelperBeanNameWithDifferentInstanceFails() {
    var duplicateRunner = new ApplicationContextRunner()
            .withUserConfiguration(SpringHelperConfiguration.class,
                    DuplicateHelperConfiguration.class);
    duplicateRunner.run(ctx -> {
      var initializer = ctx.getBean(HelperBeanRegistryInitializer.class);
      assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments()))
              .isInstanceOf(IllegalStateException.class)
              .hasMessageContaining("Helper name already registered");
    });
  }

  @Test
  void helperBeansAreReRegisteredAfterGlobalManagerReplace() {
    runner.run(ctx -> {
      HelperBeanRegistryInitializer initializer = ctx.getBean(HelperBeanRegistryInitializer.class);
      GlobalManager original = GlobalManager.globalManager();
      assertThat(original.hasHelper("testHelper")).isTrue();

      GlobalManager.globalManager().resetForTests();
      GlobalManager replacement = GlobalManager.globalManager();
      assertThat(replacement.hasHelper("testHelper")).isFalse();

      initializer.onGlobalManagerReplaced(replacement);

      assertThat(replacement.hasHelper("testHelper")).isTrue();
      Result<?> result = replacement.runHelper("testHelper", replacement.createContext(
              "Nova", Map.of(), ExecutionMode.PREVIEW, "helper-bean-replace-test"));
      assertThat(result.isSuccess()).isTrue();
      assertThat(result.value()).isEqualTo("Hello, Nova!");
    });
  }

  @Configuration
  static class TestHelperConfiguration {

    @HelperBean("testHelper")
    public TestHelper testHelper() {
      return new TestHelper();
    }

    @org.springframework.context.annotation.Bean
    public HttpClient httpClient() {
      return HttpClient.newHttpClient();
    }

    @org.springframework.context.annotation.Bean
    public CbsNovaLoggingProperties loggingProperties() {
      return new CbsNovaLoggingProperties(Level.INFO, Level.INFO, true);
    }
  }

  @Configuration
  static class SameInstanceHelperConfiguration {

    @HelperBean("sharedTestHelper")
    public TestHelper sharedTestHelper() {
      return SHARED_HELPER;
    }

    @org.springframework.context.annotation.Bean
    public HttpClient httpClient() {
      return HttpClient.newHttpClient();
    }

    @org.springframework.context.annotation.Bean
    public CbsNovaLoggingProperties loggingProperties() {
      return new CbsNovaLoggingProperties(Level.INFO, Level.INFO, true);
    }
  }

  @Configuration
  static class DuplicateHelperConfiguration {

    @HelperBean("duplicateHelper")
    public TestHelper duplicateHelperOne() {
      return new TestHelper();
    }

    @HelperBean("duplicateHelper")
    public AnotherTestHelper duplicateHelperTwo() {
      return new AnotherTestHelper();
    }

    @org.springframework.context.annotation.Bean
    public HttpClient httpClient() {
      return HttpClient.newHttpClient();
    }

    @org.springframework.context.annotation.Bean
    public CbsNovaLoggingProperties loggingProperties() {
      return new CbsNovaLoggingProperties(Level.INFO, Level.INFO, true);
    }
  }

  public static class TestHelper implements Executable<String, String> {

    @Override
    public Result<String> execute(Context<String> ctx) {
      return Result.success("Hello, " + ctx.body() + "!");
    }
  }

  public static class AnotherTestHelper implements Executable<String, String> {

    @Override
    public Result<String> execute(Context<String> ctx) {
      return Result.success("Goodbye, " + ctx.body() + "!");
    }
  }
}
