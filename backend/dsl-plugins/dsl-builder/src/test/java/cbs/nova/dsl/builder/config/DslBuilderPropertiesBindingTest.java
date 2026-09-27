package cbs.nova.dsl.builder.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class DslBuilderPropertiesBindingTest {

  private final ApplicationContextRunner runner = new ApplicationContextRunner()
          .withUserConfiguration(BindingConfig.class)
          .withPropertyValues("cbs.dsl.builder.workspace-dir=" + tempDir());

  private static String tempDir() {
    return Path.of(System.getProperty("java.io.tmpdir"), "dsl-builder-bind-test").toString();
  }

  @Test
  void workspaceModeDefaultsToShared() {
    runner.run(ctx -> assertThat(ctx.getBean(DslBuilderProperties.class).workspaceMode())
            .isEqualTo(WorkspaceMode.SHARED));
  }

  @Test
  void workspaceModeBindsWorktree() {
    runner.withPropertyValues("cbs.dsl.builder.workspace-mode=WORKTREE")
            .run(ctx -> assertThat(ctx.getBean(DslBuilderProperties.class).workspaceMode())
                    .isEqualTo(WorkspaceMode.WORKTREE));
  }

  @Test
  void workspaceModeBindsCaseInsensitively() {
    runner.withPropertyValues("cbs.dsl.builder.workspace-mode=worktree")
            .run(ctx -> assertThat(ctx.getBean(DslBuilderProperties.class).workspaceMode())
                    .isEqualTo(WorkspaceMode.WORKTREE));
  }

  @Test
  void nullModeCoercesToShared() {
    assertThat(WorkspaceMode.from(null)).isEqualTo(WorkspaceMode.SHARED);
    assertThat(WorkspaceMode.from(" ")).isEqualTo(WorkspaceMode.SHARED);
    assertThat(WorkspaceMode.from("bogus")).isEqualTo(WorkspaceMode.SHARED);
  }

  @EnableConfigurationProperties(DslBuilderProperties.class)
  static class BindingConfig {
  }
}
