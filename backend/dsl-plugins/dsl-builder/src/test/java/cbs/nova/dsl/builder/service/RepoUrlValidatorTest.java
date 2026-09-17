package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Adapter-level tests: verifies {@link DslBuilderProperties} maps onto the shared
 * {@link cbs.nova.dsl.utils.UrlSafetyValidator} and that error messages carry the {@code repoUrl}
 * label. Full validation-matrix coverage lives in {@code UrlSafetyValidatorTest} (dsl-api).
 */
class RepoUrlValidatorTest {

  @TempDir
  Path workspaceDir;

  private DslBuilderProperties properties(
          List<String> allowedRepoSchemes,
          boolean allowPlainHttpRepo,
          List<String> allowedRepoHosts) {
    return new DslBuilderProperties(
            workspaceDir,
            Duration.ofMinutes(10),
            Duration.ofHours(1),
            null,
            "0.0.1-SNAPSHOT",
            "1.27.0",
            "4.0.4",
            "v1",
            List.of("clean", "build"),
            List.of("dsl", "models"),
            "project/templates",
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            allowedRepoSchemes,
            allowPlainHttpRepo,
            allowedRepoHosts);
  }

  private DslBuilderProperties defaultProperties() {
    return properties(null, false, null);
  }

  @Test
  void mapsDefaultConfigAndAllowsHttps() {
    assertThatCode(() -> RepoUrlValidator.validate("https://github.com/org/repo.git",
            defaultProperties())).doesNotThrowAnyException();
  }

  @Test
  void mapsAllowedSchemesFromProperties() {
    assertThatThrownBy(() -> RepoUrlValidator.validate("ssh://git@git.example/repo.git",
            defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class);

    var allowSsh = properties(List.of("https", "ssh"), false, null);
    assertThatCode(() -> RepoUrlValidator.validate("ssh://git.example/repo.git", allowSsh))
            .doesNotThrowAnyException();
  }

  @Test
  void mapsAllowPlainHttpFlagFromProperties() {
    assertThatThrownBy(() -> RepoUrlValidator.validate("http://git.example/repo.git",
            defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("http");

    var allowHttp = properties(List.of("https", "http"), true, null);
    assertThatCode(() -> RepoUrlValidator.validate("http://git.example/repo.git", allowHttp))
            .doesNotThrowAnyException();
  }

  @Test
  void mapsAllowedHostsFromProperties() {
    var restricted = properties(null, false, List.of("github.com"));

    assertThatCode(() -> RepoUrlValidator.validate("https://github.com/org/repo.git", restricted))
            .doesNotThrowAnyException();
    assertThatThrownBy(() -> RepoUrlValidator.validate("https://gitlab.com/org/repo.git",
            restricted))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("host is not allowed");
  }

  @Test
  void rejectionMessageCarriesRepoUrlLabel() {
    assertThatThrownBy(() -> RepoUrlValidator.validate("ftp://example.com/repo.git",
            defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("repoUrl");

    assertThatThrownBy(
            () -> RepoUrlValidator.validate("https://exa mple.com/\\repo", defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Invalid repoUrl");
  }
}
