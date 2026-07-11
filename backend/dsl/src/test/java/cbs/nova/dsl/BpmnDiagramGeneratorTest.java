package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.generator.BpmnDiagramGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class BpmnDiagramGeneratorTest {

  private final BpmnDiagramGenerator generator = new BpmnDiagramGenerator();

  @Test
  void processWithoutCompensationContainsActivityAndEndEvent() {
    var process = Dsl.process("LoanDisbursement")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    String xml = generator.forProcess(process);
    assertThat(xml).containsSubsequence(
            "<bpmn:process id=\"Process_1\"",
            "<bpmn:serviceTask id=\"Activity_1\" name=\"LoanDisbursement\"",
            "<bpmn:endEvent id=\"EndEvent_1\" name=\"End\"",
            "</bpmn:endEvent>",
            "</bpmn:process>",
            "</bpmn:definitions>");
    assertThat(xml).contains("sourceRef=\"StartEvent_1\" targetRef=\"Activity_1\"");
    assertThat(xml).contains("sourceRef=\"Activity_1\" targetRef=\"EndEvent_1\"");
    assertThat(xml).doesNotContain("Compensate");
  }

  @Test
  void processWithCompensationContainsGatewayAndCompensate() {
    var process = Dsl.process("LoanDisbursement")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> Result.success("rolled back"))
            .build();
    String xml = generator.forProcess(process);
    assertThat(xml).contains("<bpmn:exclusiveGateway id=\"Gateway_1\" name=\"Success?\"");
    assertThat(xml).contains("<bpmn:serviceTask id=\"Activity_Compensate\" name=\"Compensate\"");
    assertThat(xml).contains("<bpmn:sequenceFlow id=\"Flow_Success\" name=\"Yes\"");
  }

  @Test
  void transactionGeneratesDiagram() {
    var tx = Dsl.transaction("KycCheck")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    String xml = generator.forTransaction(tx);
    assertThat(xml).contains("<bpmn:serviceTask id=\"Activity_1\" name=\"KycCheck\"");
  }

  @Test
  void helperGeneratesDiagram() {
    String xml = generator.forHelper("MyHelper");
    assertThat(xml).contains("<bpmn:serviceTask id=\"Activity_1\" name=\"MyHelper\"");
  }

  @Test
  void externalCallsAreRenderedInDetailedDiagram() {
    var process = Dsl.process("TrackedProcess")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    List<Map<String, Object>> calls = List.of(
            Map.of("type", "http", "target", "payment-api", "operation", "POST /pay"),
            Map.of("type", "database", "target", "user-db", "operation", "SELECT"));

    String xml = generator.forProcess(process, calls, null);
    assertThat(xml)
            .contains("<bpmn:serviceTask id=\"Activity_1\" name=\"HTTP POST /pay (payment-api)\"");
    assertThat(xml)
            .contains("<bpmn:serviceTask id=\"Activity_2\" name=\"DATABASE SELECT (user-db)\"");
    assertThat(xml).contains("<bpmn:serviceTask id=\"Activity_3\" name=\"TrackedProcess\"");
    assertThat(xml).contains("sourceRef=\"StartEvent_1\" targetRef=\"Activity_1\"");
    assertThat(xml).contains("sourceRef=\"Activity_2\" targetRef=\"Activity_3\"");
  }

  @Test
  void callCountsAreRenderedAsComment() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    String xml = generator.forProcess(process, List.of(), Map.of("http", 2, "database", 1));
    assertThat(xml).contains("<!-- Call Counts: database: 1, http: 2 -->");
  }

  @Test
  void longCallTargetIsTruncated() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    List<Map<String, Object>> calls = List.of(
            Map.of("type", "http", "target", "a-very-long-target-name-that-exceeds-twenty",
                    "operation", "GET"));
    String xml = generator.forProcess(process, calls, null);
    assertThat(xml).contains("a-very-long-targe...");
    assertThat(xml).doesNotContain("a-very-long-target-name-that-exceeds-twenty");
  }

  @Test
  void transactionWithCompensationRendersCompensateBranch() {
    var tx = Dsl.transaction("KycCheck")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> Result.success("rolled back"))
            .build();
    String xml = generator.forTransaction(tx);
    assertThat(xml).contains("<bpmn:serviceTask id=\"Activity_1\" name=\"KycCheck\"");
    assertThat(xml).contains("<bpmn:exclusiveGateway id=\"Gateway_1\" name=\"Success?\"");
    assertThat(xml).contains("<bpmn:serviceTask id=\"Activity_Compensate\" name=\"Compensate\"");
    assertThat(xml).contains("<bpmn:sequenceFlow id=\"Flow_Success\" name=\"Yes\"");
    assertThat(xml).doesNotContain("name=\"Fail\"");
  }

  @Test
  void transactionWithExternalCallsRendersCallNodesAndMainActivity() {
    var tx = Dsl.transaction("TrackedTx")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    List<Map<String, Object>> calls = List.of(
            Map.of("type", "http", "target", "payment-api", "operation", "POST /pay"),
            Map.of("type", "database", "target", "user-db", "operation", "SELECT"));
    String xml = generator.forTransaction(tx, calls, null);
    assertThat(xml)
            .contains("<bpmn:serviceTask id=\"Activity_1\" name=\"HTTP POST /pay (payment-api)\"");
    assertThat(xml)
            .contains("<bpmn:serviceTask id=\"Activity_2\" name=\"DATABASE SELECT (user-db)\"");
    assertThat(xml).contains("<bpmn:serviceTask id=\"Activity_3\" name=\"TrackedTx\"");
    assertThat(xml).contains("sourceRef=\"StartEvent_1\" targetRef=\"Activity_1\"");
    assertThat(xml).contains("sourceRef=\"Activity_2\" targetRef=\"Activity_3\"");
  }

  @Test
  void helperWithExternalCallsRendersCallNodesAndMainActivity() {
    String xml = generator.forHelper("TrackedHelper", List.of(
            Map.of("type", "http", "target", "payment-api", "operation", "POST /pay"),
            Map.of("type", "database", "target", "user-db", "operation", "SELECT")), null);
    assertThat(xml)
            .contains("<bpmn:serviceTask id=\"Activity_1\" name=\"HTTP POST /pay (payment-api)\"");
    assertThat(xml)
            .contains("<bpmn:serviceTask id=\"Activity_2\" name=\"DATABASE SELECT (user-db)\"");
    assertThat(xml).contains("<bpmn:serviceTask id=\"Activity_3\" name=\"TrackedHelper\"");
    assertThat(xml).contains("sourceRef=\"StartEvent_1\" targetRef=\"Activity_1\"");
    assertThat(xml).contains("sourceRef=\"Activity_2\" targetRef=\"Activity_3\"");
  }

  @Test
  void helperWithExternalCallsAndCallCountsRendersComment() {
    String xml = generator.forHelper("TrackedHelper", List.of(
            Map.of("type", "http", "target", "payment-api", "operation", "POST /pay")),
            Map.of("http", 3, "database", 1));
    assertThat(xml)
            .contains("<bpmn:serviceTask id=\"Activity_1\" name=\"HTTP POST /pay (payment-api)\"");
    assertThat(xml).contains("<!-- Call Counts: database: 1, http: 3 -->");
  }

  @Test
  void emptyCallListAndEmptyCallCountsProduceNoCallCountComment() {
    var process = Dsl.process("P")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.success("ok"))
            .build();
    String xml = generator.forProcess(process, List.of(), Map.of());
    assertThat(xml).doesNotContain("Call Counts:");
  }
}
