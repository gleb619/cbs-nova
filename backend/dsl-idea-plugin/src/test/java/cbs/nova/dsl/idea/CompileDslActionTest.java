package cbs.nova.dsl.idea;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CompileDslActionTest {

  @Test
  void buildsGradleCommandForModule() {
    var command = CompileDslAction.gradleCommand("dsl-examples");
    assertThat(command).containsExactly("./gradlew", ":dsl-examples:compileDsl");
  }
}
