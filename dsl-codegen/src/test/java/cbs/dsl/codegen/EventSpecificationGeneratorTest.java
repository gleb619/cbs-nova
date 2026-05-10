package cbs.dsl.codegen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

class EventSpecificationGeneratorTest {

  @TempDir
  Path tempDir;

  @Test
  @DisplayName("shouldGenerateWorkflowInterfaceAndImplementation")
  void shouldGenerateWorkflowInterfaceAndImplementation() throws Exception {
    EventSpecificationModel spec = new EventSpecificationModel(
        "LOAN_DISBURSEMENT", "com.example.Loan", List.of("DEBIT_ACCOUNT"));

    EventSpecificationGenerator gen = new EventSpecificationGenerator(tempDir);
    List<DslCompiler.FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path interfacePath =
        tempDir.resolve("cbs/dsl/codegen/generated/LoanDisbursementEventSpecification.java");
    Path implPath =
        tempDir.resolve("cbs/dsl/codegen/generated/LoanDisbursementEventSpecificationImpl.java");

    assertTrue(Files.exists(interfacePath), "Should generate workflow interface");
    assertTrue(Files.exists(implPath), "Should generate workflow implementation");

    String interfaceContent = Files.readString(interfacePath);
    assertTrue(
        interfaceContent.contains("@WorkflowInterface"),
        "Interface should have @WorkflowInterface");
    assertTrue(
        interfaceContent.contains(
            "EventOutput execute(EventInput input)"),
        "Interface should have execute method");
    assertTrue(
        interfaceContent.contains("@WorkflowMethod(name = \"LOAN_DISBURSEMENT\")"),
        "Interface should have correct workflow method name");

    String implContent = Files.readString(implPath);
    assertNotNull(implContent);
    assertTrue(
        implContent.contains(
            "class LoanDisbursementEventSpecificationImpl implements LoanDisbursementEventSpecification"),
        "Impl should implement interface");
    assertTrue(
        implContent.contains("import cbs.nova.temporal.ActivityManager;"),
        "Impl should import ActivityManager");
  }

  @Test
  @DisplayName("shouldNotUseStaticActivityManagerFieldAndUseDirectGetInstanceInConstructor")
  void shouldNotUseStaticActivityManagerFieldAndUseDirectGetInstanceInConstructor()
      throws Exception {
    EventSpecificationModel spec =
        new EventSpecificationModel("TEST_EVENT", "com.example.Test", List.of("TX_1"));

    EventSpecificationGenerator gen = new EventSpecificationGenerator(tempDir);
    List<DslCompiler.FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path implPath =
        tempDir.resolve("cbs/dsl/codegen/generated/TestEventEventSpecificationImpl.java");
    String implContent = Files.readString(implPath);

    assertFalse(
        implContent.contains("private static final ActivityManager activityManager"),
        "Generated workflow should not contain static ActivityManager field to minimize Temporal side effects");
    assertTrue(
        implContent.contains("ActivityManager.getInstance().newActivityStub("),
        "Generated workflow should call ActivityManager.getInstance() directly in constructor");
  }

  @Test
  @DisplayName("shouldWireTransactionActivitiesInConstructor")
  void shouldWireTransactionActivitiesInConstructor() throws Exception {
    EventSpecificationModel spec = new EventSpecificationModel(
        "PAYMENT", "com.example.Payment", List.of("DEBIT_ACCOUNT", "CREDIT_ACCOUNT"));

    EventSpecificationGenerator gen = new EventSpecificationGenerator(tempDir);
    List<DslCompiler.FileWrite> files = gen.generate(List.of(spec));
    gen.write(files);

    Path implPath = tempDir.resolve("cbs/dsl/codegen/generated/PaymentEventSpecificationImpl.java");
    String implContent = Files.readString(implPath);

    assertTrue(
        implContent.contains("private final DebitAccountTransactionActivity debitAccountActivity;"),
        "Should declare debit account activity field");
    assertTrue(
        implContent.contains(
            "private final CreditAccountTransactionActivity creditAccountActivity;"),
        "Should declare credit account activity field");
    assertTrue(
        implContent.contains(
            "this.debitAccountActivity = ActivityManager.getInstance().newActivityStub("),
        "Should initialize debit account activity stub");
    assertTrue(
        implContent.contains("\"DEBIT_ACCOUNT\", DebitAccountTransactionActivity.class"),
        "Should reference DEBIT_ACCOUNT activity");
    assertTrue(
        implContent.contains(
            "this.creditAccountActivity = ActivityManager.getInstance().newActivityStub("),
        "Should initialize credit account activity stub");
    assertTrue(
        implContent.contains("\"CREDIT_ACCOUNT\", CreditAccountTransactionActivity.class"),
        "Should reference CREDIT_ACCOUNT activity");
  }
}
