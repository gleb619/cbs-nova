package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.generator.PlantUmlDiagramGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class PlantUmlDiagramGeneratorTest {

  private final PlantUmlDiagramGenerator generator = new PlantUmlDiagramGenerator();

  @Test
  void processWithoutCompensationContainsActivity() {
    var process = Dsl.process("LoanDisbursement")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    String diagram = generator.forProcess(process);
    assertThat(diagram).startsWith("@startuml");
    assertThat(diagram).contains(":LoanDisbursement;");
    assertThat(diagram).contains("stop");
    assertThat(diagram).endsWith("@enduml");
    assertThat(diagram).doesNotContain("Compensate");
  }

  @Test
  void processWithCompensationContainsCompensateBranch() {
    var process = Dsl.process("LoanDisbursement")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> Result.success("rolled back"))
            .build();
    String diagram = generator.forProcess(process);
    assertThat(diagram).contains("if (success?) then (yes)");
    assertThat(diagram).contains(":Compensate;");
    assertThat(diagram).contains("endif");
  }

  @Test
  void transactionGeneratesDiagram() {
    var tx = Dsl.transaction("KycCheck")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    String diagram = generator.forTransaction(tx);
    assertThat(diagram).contains(":KycCheck;");
  }

  @Test
  void helperGeneratesDiagram() {
    String diagram = generator.forHelper("MyHelper");
    assertThat(diagram).contains(":MyHelper;");
  }

  @Test
  void externalCallsAreRendered() {
    var process = Dsl.process("TrackedProcess")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    List<Map<String, Object>> calls = List.of(
            Map.of("type", "http", "target", "payment-api", "operation", "POST /pay"),
            Map.of("type", "database", "target", "user-db", "operation", "SELECT"));

    String diagram = generator.forProcess(process, calls, null);
    assertThat(diagram).contains("HTTP POST /pay (payment-api)");
    assertThat(diagram).contains("DATABASE SELECT (user-db)");
  }

  @Test
  void callCountsAreRendered() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    String diagram = generator.forProcess(process, List.of(), Map.of("http", 2, "database", 1));
    assertThat(diagram).contains("' Call Counts: database: 1, http: 2");
  }

  @Test
  void longCallTargetIsTruncated() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    List<Map<String, Object>> calls = List.of(
            Map.of("type", "http", "target",
                    "a-very-long-target-name-that-exceeds-thirty-characters", "operation", "GET"));
    String diagram = generator.forProcess(process, calls, null);
    assertThat(diagram).contains("a-very-long-target-name-tha...");
    assertThat(diagram).doesNotContain("a-very-long-target-name-that-exceeds-thirty-characters");
  }
}
