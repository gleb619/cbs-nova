package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MermaidDiagramGeneratorTest {

  @Test
  void processWithoutCompensationHasFailEdge() {
    var process = Dsl.process("LoanDisbursement").execute(ctx -> Result.success("ok")).build();
    String diagram = MermaidDiagramGenerator.forProcess(process);
    assertThat(diagram).contains("Execute[LoanDisbursement]");
    assertThat(diagram).contains("|failure| Fail");
    assertThat(diagram).doesNotContain("Compensate");
  }

  @Test
  void processWithCompensationHasCompensateEdge() {
    var process = Dsl.process("LoanDisbursement")
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> Result.success("rolled back"))
            .build();
    String diagram = MermaidDiagramGenerator.forProcess(process);
    assertThat(diagram).contains("Compensate[Compensate]");
    assertThat(diagram).doesNotContain("Fail");
  }

  @Test
  void transactionGeneratesDiagram() {
    var tx = Dsl.transaction("KycCheck").execute(ctx -> Result.success("ok")).build();
    String diagram = MermaidDiagramGenerator.forTransaction(tx);
    assertThat(diagram).contains("Activity[KycCheck]");
  }
}
