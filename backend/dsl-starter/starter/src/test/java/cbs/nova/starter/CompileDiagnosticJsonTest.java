package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.model.CompileDiagnostic;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CompileDiagnosticJsonTest {

  private final ObjectMapper mapper = new ObjectMapper();

  @Test
  void serializesCodeWhenPresent() throws Exception {
    var diagnostic = new CompileDiagnostic("file.java", 1L, 2L, "bad", "error",
            "compiler.err.expected");

    String json = mapper.writeValueAsString(diagnostic);

    assertThat(json).contains("\"code\":\"compiler.err.expected\"");
  }

  @Test
  void omitsCodeWhenNull() throws Exception {
    var diagnostic = new CompileDiagnostic("file.java", 1L, 2L, "bad", "error", null);

    String json = mapper.writeValueAsString(diagnostic);

    assertThat(json).doesNotContain("\"code\"");
  }

}
