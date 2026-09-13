package cbs.nova.dsl.codegen;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import cbs.nova.dsl.model.DiagnosticCodes;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.exception.ValidationException;
import cbs.nova.dsl.model.ValidationIssue;
import cbs.nova.dsl.config.DescriptorFactory;
import cbs.nova.dsl.function.FunctionDescriptor;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.registry.HelperRegistry;
import cbs.nova.dsl.transaction.TransactionDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;

class SemanticValidatorTest {

  private final HelperRegistry emptyRegistry = new DefaultHelperRegistry();

  @Test
  void happyPathNoErrors() {
    var p = new DescriptorFactory().fromProcess(
            Dsl.process("P")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());
    var t = new DescriptorFactory().fromTransaction(
            Dsl.transaction("T")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());
    var f = new DescriptorFactory().fromFunction(
            Dsl.function("F").execute(ctx -> Result.success("ok")).build());

    assertThatCode(
            () -> new SemanticValidator().validate(List.of(p), List.of(t), List.of(f),
                    emptyRegistry))
            .doesNotThrowAnyException();
  }

  @Test
  void blankProcessNameEmitsCodeAndMessage() {
    var p = ProcessDescriptor.builder()
            .name("  ")
            .version("v1")
            .taskQueue("q")
            .inputType(String.class)
            .outputType(String.class)
            .helperRefs(List.of())
            .transactionRefs(List.of())
            .build();

    var ex = catchThrowableOfType(
            ValidationException.class,
            () -> new SemanticValidator().validate(
                    List.of(p), List.of(), List.of(), emptyRegistry));

    assertThat(ex.issues()).containsExactly(
            new ValidationIssue(DiagnosticCodes.BLANK_PROCESS_NAME, "Process has blank name"));
    assertThat(ex.errors()).containsExactly("Process has blank name");
  }

  @Test
  void blankTransactionNameEmitsCodeAndMessage() {
    var t = TransactionDescriptor.builder()
            .name("")
            .version("v1")
            .taskQueue("q")
            .inputType(String.class)
            .outputType(String.class)
            .helperRefs(List.of())
            .build();

    var ex = catchThrowableOfType(
            ValidationException.class,
            () -> new SemanticValidator().validate(
                    List.of(), List.of(t), List.of(), emptyRegistry));

    assertThat(ex.issues()).containsExactly(
            new ValidationIssue(DiagnosticCodes.BLANK_TRANSACTION_NAME,
                    "Transaction has blank name"));
  }

  @Test
  void blankFunctionNameEmitsCodeAndMessage() {
    var f = FunctionDescriptor.builder().name("   ").build();

    var ex = catchThrowableOfType(
            ValidationException.class,
            () -> new SemanticValidator().validate(
                    List.of(), List.of(), List.of(f), emptyRegistry));

    assertThat(ex.issues()).containsExactly(
            new ValidationIssue(DiagnosticCodes.BLANK_FUNCTION_NAME,
                    "Function has blank name"));
  }

  @Test
  void duplicateNameThrowsWithCodeAndUnchangedMessage() {
    var p1 = new DescriptorFactory().fromProcess(
            Dsl.process("Dup")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());
    var p2 = new DescriptorFactory().fromProcess(
            Dsl.process("Dup")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var ex = catchThrowableOfType(
            ValidationException.class,
            () -> new SemanticValidator().validate(
                    List.of(p1, p2), List.of(), List.of(), emptyRegistry));

    assertThat(ex).isNotNull();
    assertThat(ex.errors()).anyMatch(e -> e.contains("Dup"));
    assertThat(ex.issues()).anySatisfy(i -> {
      assertThat(i.code()).isEqualTo(DiagnosticCodes.DUPLICATE_NAME);
      assertThat(i.message()).isEqualTo("Duplicate name: Dup");
    });
  }

  @Test
  void unknownHelperRefThrowsWithCodeAndUnchangedMessage() {
    var p = ProcessDescriptor.builder()
            .name("P")
            .version("v1")
            .taskQueue("P-queue")
            .inputType(String.class)
            .outputType(String.class)
            .helperRefs(List.of("unknownHelper"))
            .transactionRefs(List.of())
            .build();

    var ex = catchThrowableOfType(
            ValidationException.class,
            () -> new SemanticValidator().validate(
                    List.of(p), List.of(), List.of(), emptyRegistry));

    assertThat(ex).isNotNull();
    assertThat(ex.errors()).anyMatch(e -> e.contains("unknownHelper"));
    assertThat(ex.issues()).containsExactly(
            new ValidationIssue(DiagnosticCodes.UNKNOWN_HELPER,
                    "Process 'P' references unknown helper: unknownHelper"));
  }

  @Test
  void unknownTransactionHelperRefThrowsWithSameCode() {
    var t = TransactionDescriptor.builder()
            .name("T")
            .version("v1")
            .taskQueue("T-queue")
            .inputType(String.class)
            .outputType(String.class)
            .helperRefs(List.of("missingHelper"))
            .build();

    var ex = catchThrowableOfType(
            ValidationException.class,
            () -> new SemanticValidator().validate(
                    List.of(), List.of(t), List.of(), emptyRegistry));

    assertThat(ex.issues()).containsExactly(
            new ValidationIssue(DiagnosticCodes.UNKNOWN_HELPER,
                    "Transaction 'T' references unknown helper: missingHelper"));
  }

  @Test
  void structuredIssuesExposeCodesIndependentlyOfMessages() {
    var p = ProcessDescriptor.builder()
            .name("Bad Name")
            .version("v1")
            .taskQueue("q")
            .inputType(String.class)
            .outputType(String.class)
            .helperRefs(List.of("h1", "h2"))
            .transactionRefs(List.of())
            .build();
    var p2 = ProcessDescriptor.builder()
            .name("Bad Name")
            .version("v1")
            .taskQueue("q")
            .inputType(String.class)
            .outputType(String.class)
            .helperRefs(List.of())
            .transactionRefs(List.of())
            .build();

    var ex = catchThrowableOfType(
            ValidationException.class,
            () -> new SemanticValidator().validate(
                    List.of(p, p2), List.of(), List.of(), emptyRegistry));

    assertThat(ex.issues()).extracting(ValidationIssue::code)
            .contains(DiagnosticCodes.UNKNOWN_HELPER, DiagnosticCodes.DUPLICATE_NAME);
    assertThat(ex.issues()).extracting(ValidationIssue::message)
            .contains("Process 'Bad Name' references unknown helper: h1",
                    "Process 'Bad Name' references unknown helper: h2",
                    "Duplicate name: Bad Name");
  }

  @Test
  void knownHelperRefPassesValidation() {
    var registry = new DefaultHelperRegistry();
    registry.registerHelper("myHelper", ctx -> Result.success("x"));

    var p = ProcessDescriptor.builder()
            .name("P")
            .version("v1")
            .taskQueue("P-queue")
            .inputType(String.class)
            .outputType(String.class)
            .helperRefs(List.of("myHelper"))
            .transactionRefs(List.of())
            .build();

    assertThatCode(
            () -> new SemanticValidator().validate(List.of(p), List.of(), List.of(), registry))
            .doesNotThrowAnyException();
  }
}
