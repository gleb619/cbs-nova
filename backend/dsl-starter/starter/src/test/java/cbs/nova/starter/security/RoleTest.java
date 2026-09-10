package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * Verifies the {@link Role} enum hierarchy used by the RBAC filter (T408).
 */
class RoleTest {

  @Test
  void ranksAreStrictlyIncreasingFromViewerToOperator() {
    assertThat(Role.VIEWER.rank()).isLessThan(Role.RUNNER.rank());
    assertThat(Role.RUNNER.rank()).isLessThan(Role.AUTHOR.rank());
    assertThat(Role.AUTHOR.rank()).isLessThan(Role.OPERATOR.rank());
    assertThat(Role.OPERATOR.rank()).isLessThan(Role.ADMIN.rank());
  }

  @Test
  void adminSatisfiesEveryRequiredRoleIncludingItself() {
    for (Role required : Role.values()) {
      assertThat(Role.ADMIN.satisfies(required))
              .as("ADMIN must satisfy %s", required)
              .isTrue();
    }
  }

  @Test
  void viewerSatisfiesOnlyViewer() {
    assertThat(Role.VIEWER.satisfies(Role.VIEWER)).isTrue();
    assertThat(Role.VIEWER.satisfies(Role.RUNNER)).isFalse();
    assertThat(Role.VIEWER.satisfies(Role.AUTHOR)).isFalse();
    assertThat(Role.VIEWER.satisfies(Role.OPERATOR)).isFalse();
    assertThat(Role.VIEWER.satisfies(Role.ADMIN)).isFalse();
  }

  @Test
  void runnerSatisfiesViewerAndRunnerButNotAuthor() {
    assertThat(Role.RUNNER.satisfies(Role.VIEWER)).isTrue();
    assertThat(Role.RUNNER.satisfies(Role.RUNNER)).isTrue();
    assertThat(Role.RUNNER.satisfies(Role.AUTHOR)).isFalse();
    assertThat(Role.RUNNER.satisfies(Role.OPERATOR)).isFalse();
    assertThat(Role.RUNNER.satisfies(Role.ADMIN)).isFalse();
  }

  @Test
  void authorSatisfiesViewerRunnerAndAuthor() {
    assertThat(Role.AUTHOR.satisfies(Role.VIEWER)).isTrue();
    assertThat(Role.AUTHOR.satisfies(Role.RUNNER)).isTrue();
    assertThat(Role.AUTHOR.satisfies(Role.AUTHOR)).isTrue();
    assertThat(Role.AUTHOR.satisfies(Role.OPERATOR)).isFalse();
    assertThat(Role.AUTHOR.satisfies(Role.ADMIN)).isFalse();
  }

  @Test
  void operatorSatisfiesEverythingExceptAdmin() {
    assertThat(Role.OPERATOR.satisfies(Role.VIEWER)).isTrue();
    assertThat(Role.OPERATOR.satisfies(Role.RUNNER)).isTrue();
    assertThat(Role.OPERATOR.satisfies(Role.AUTHOR)).isTrue();
    assertThat(Role.OPERATOR.satisfies(Role.OPERATOR)).isTrue();
    assertThat(Role.OPERATOR.satisfies(Role.ADMIN)).isFalse();
  }

  @Test
  void satisfiesReturnsFalseForNullRequired() {
    assertThat(Role.ADMIN.satisfies(null)).isFalse();
    assertThat(Role.VIEWER.satisfies(null)).isFalse();
  }
}
