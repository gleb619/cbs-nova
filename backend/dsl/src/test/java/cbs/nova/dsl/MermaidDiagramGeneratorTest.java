package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.generator.MermaidDiagramGenerator;
import org.junit.jupiter.api.Test;

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
}
