package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.generator.MermaidDiagramGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class MermaidDiagramGeneratorTest {

  private final MermaidDiagramGenerator generator = new MermaidDiagramGenerator();

  @Test
  void processWithoutCompensationHasFailEdge() {
    var process = Dsl.process("LoanDisbursement")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    String diagram = generator.forProcess(process);
    assertThat(diagram).contains("Execute[LoanDisbursement]");
    assertThat(diagram).contains("|failure| Fail");
    assertThat(diagram).doesNotContain("Compensate");
  }

  @Test
  void processWithCompensationHasCompensateEdge() {
    var process = Dsl.process("LoanDisbursement")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> Result.success("rolled back"))
            .build();
    String diagram = generator.forProcess(process);
    assertThat(diagram).contains("Compensate[Compensate]");
    assertThat(diagram).doesNotContain("Fail");
  }

  @Test
  void transactionGeneratesDiagram() {
    var tx = Dsl.transaction("KycCheck")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    String diagram = generator.forTransaction(tx);
    assertThat(diagram).contains("Activity[KycCheck]");
  }

  @Test
  void transactionWithCompensationHasCompensateEdge() {
    var tx = Dsl.transaction("KycCheck")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> Result.success("rolled back"))
            .build();
    String diagram = generator.forTransaction(tx);
    assertThat(diagram).contains("Activity[KycCheck]");
    assertThat(diagram).contains("Compensate[Compensate]");
    assertThat(diagram).doesNotContain("Fail");
  }

  @Test
  void helperGeneratesDiagram() {
    String diagram = generator.forHelper("MyHelper");
    assertThat(diagram).contains("Helper[MyHelper]");
    assertThat(diagram).contains("Helper --> End([End])");
  }

  @Test
  void processExternalCallsReferenceTypeOperationAndTarget() {
    var process = Dsl.process("TrackedProcess")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    List<Map<String, Object>> calls = List.of(
            Map.of("type", "http", "target", "payment-api", "operation", "POST /pay"),
            Map.of("type", "database", "target", "user-db", "operation", "SELECT"));
    String diagram = generator.forProcess(process, calls, null);
    assertThat(diagram).contains("Execute --> |POST /pay| http0[HTTP: payment-api]");
    assertThat(diagram).contains("Execute --> |SELECT| database1[DATABASE: user-db]");
    assertThat(diagram).containsSubsequence(
            "Execute --> |POST /pay| http0[HTTP: payment-api]",
            "Execute --> |SELECT| database1[DATABASE: user-db]",
            "Execute --> |success| End([End])");
  }

  @Test
  void transactionExternalCallsReferenceTypeOperationAndTarget() {
    var tx = Dsl.transaction("TrackedTx")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    List<Map<String, Object>> calls = List.of(
            Map.of("type", "http", "target", "payment-api", "operation", "POST /pay"),
            Map.of("type", "database", "target", "user-db", "operation", "SELECT"));
    String diagram = generator.forTransaction(tx, calls, null);
    assertThat(diagram).contains("Activity --> |POST /pay| http0[HTTP: payment-api]");
    assertThat(diagram).contains("Activity --> |SELECT| database1[DATABASE: user-db]");
  }

  @Test
  void helperExternalCallsReferenceTypeOperationAndTarget() {
    List<Map<String, Object>> calls = List.of(
            Map.of("type", "http", "target", "payment-api", "operation", "POST /pay"),
            Map.of("type", "database", "target", "user-db", "operation", "SELECT"));
    String diagram = generator.forHelper("TrackedHelper", calls, null);
    assertThat(diagram).contains("Helper --> |POST /pay| http0[HTTP: payment-api]");
    assertThat(diagram).contains("Helper --> |SELECT| database1[DATABASE: user-db]");
    assertThat(diagram).contains("Helper --> End([End])");
  }

  @Test
  void longCallTargetIsTruncatedInMermaid() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    List<Map<String, Object>> calls = List.of(
            Map.of("type", "http", "target", "a-very-long-target-name-that-exceeds-twenty",
                    "operation", "GET"));
    String diagram = generator.forProcess(process, calls, null);
    assertThat(diagram).contains("a-very-long-targe...");
    assertThat(diagram).doesNotContain("a-very-long-target-name-that-exceeds-twenty");
  }

  @Test
  void callCountsAreRenderedAsMermaidComment() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    String diagram = generator.forProcess(process, List.of(), Map.of("http", 2, "database", 1));
    assertThat(diagram).contains("%% Call Counts: database: 1, http: 2");
  }

  @Test
  void callCountsAreRenderedForHelper() {
    String diagram = generator.forHelper("TrackedHelper", List.of(), Map.of("http", 5));
    assertThat(diagram).contains("%% Call Counts: http: 5");
  }

  @Test
  void emptyCallListAndEmptyCallCountsProduceNoCallCountComment() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    String diagram = generator.forProcess(process, List.of(), Map.of());
    assertThat(diagram).doesNotContain("Call Counts:");
  }
}
