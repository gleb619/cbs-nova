package cbs.nova.dsl.idea;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CompileDslActionTest {

  @Test
  void buildsGradleCommandForModule() {
    var command = CompileDslAction.gradleCommand("dsl-examples");
    assertThat(command).containsExactly("./gradlew", ":dsl-examples:compileDsl");
  }
}
