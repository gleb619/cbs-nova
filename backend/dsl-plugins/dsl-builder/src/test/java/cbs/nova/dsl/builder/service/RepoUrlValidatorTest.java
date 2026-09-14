package cbs.nova.dsl.builder.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
  void allowsHttpsPublicHost() {
    assertThatCode(() -> RepoUrlValidator.validate("https://github.com/org/repo.git",
            defaultProperties())).doesNotThrowAnyException();
  }

  @Test
  void rejectsFileScheme() {
    assertThatThrownBy(() -> RepoUrlValidator.validate("file:///tmp/x", defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("file");
  }

  @Test
  void rejectsPlainHttpByDefault() {
    assertThatThrownBy(() -> RepoUrlValidator.validate("http://git.example/repo.git",
            defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("http");
  }

  @Test
  void allowsPlainHttpWhenEnabled() {
    var properties = properties(List.of("https", "http"), true, null);

    assertThatCode(() -> RepoUrlValidator.validate("http://git.example/repo.git", properties))
            .doesNotThrowAnyException();
  }

  @Test
  void rejectsLoopbackIpv4() {
    assertThatThrownBy(() -> RepoUrlValidator.validate("https://127.0.0.1/x",
            defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("loopback");
  }

  @Test
  void rejectsCloudMetadataAddress() {
    assertThatThrownBy(() -> RepoUrlValidator.validate("https://169.254.169.254/latest/meta-data",
            defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("link-local");
  }

  @Test
  void rejectsLoopbackIpv6() {
    assertThatThrownBy(() -> RepoUrlValidator.validate("https://[::1]/x", defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("loopback");
  }

  @Test
  void rejectsGitAndSshSchemesByDefault() {
    assertThatThrownBy(() -> RepoUrlValidator.validate("git://git.example/repo.git",
            defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> RepoUrlValidator.validate("ssh://git@git.example/repo.git",
            defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsSchemelessUrl() {
    assertThatThrownBy(() -> RepoUrlValidator.validate("github.com/org/repo.git",
            defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("scheme");
  }

  @Test
  void allowsSshWhenExplicitlyAllowlisted() {
    var properties = properties(List.of("https", "ssh"), false, null);

    assertThatCode(() -> RepoUrlValidator.validate("ssh://git.example/repo.git", properties))
            .doesNotThrowAnyException();
  }

  @Test
  void enforcesAllowedRepoHosts() {
    var properties = properties(null, false, List.of("github.com", "*.internal.example"));

    assertThatCode(() -> RepoUrlValidator.validate("https://github.com/org/repo.git", properties))
            .doesNotThrowAnyException();
    assertThatCode(() -> RepoUrlValidator.validate("https://git.internal.example/org/repo.git",
            properties)).doesNotThrowAnyException();
    assertThatThrownBy(() -> RepoUrlValidator.validate("https://gitlab.com/org/repo.git",
            properties))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("host is not allowed");
  }

  @Test
  void wildcardHostEntryDoesNotMatchApex() {
    var properties = properties(null, false, List.of("*.internal.example"));

    assertThatThrownBy(() -> RepoUrlValidator.validate("https://internal.example/org/repo.git",
            properties))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("host is not allowed");
  }

  @Test
  void localAddressCheckAppliesEvenWithoutHostAllowlist() {
    var properties = properties(null, false, List.of());

    assertThatThrownBy(() -> RepoUrlValidator.validate("https://192.168.1.10/repo.git",
            properties))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("site-local");
    assertThatThrownBy(() -> RepoUrlValidator.validate("https://localhost/repo.git", properties))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("loopback");
  }

  @Test
  void neverLeaksUserInfoIntoRejectionMessage() {
    assertThatThrownBy(() -> RepoUrlValidator.validate("https://user:token@127.0.0.1/x",
            defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining("user:token")
            .hasMessageNotContaining("token");

    var restricted = properties(null, false, List.of("github.com"));
    assertThatThrownBy(() -> RepoUrlValidator.validate("https://user:token@gitlab.com/x",
            restricted))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageNotContaining("user:token")
            .hasMessageNotContaining("token");
  }

  @Test
  void rejectsUnparseableUrl() {
    assertThatThrownBy(() -> RepoUrlValidator.validate("https://exa mple.com/\\repo",
            defaultProperties()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not a valid URI");
  }
}
