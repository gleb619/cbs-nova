package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import cbs.nova.dsl.registry.DefaultHelperRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;

class SemanticValidatorTest {
  private final HelperRegistry emptyRegistry = new DefaultHelperRegistry();

  @Test
  void happyPathNoErrors() {
    var p = DescriptorFactory.fromProcess(
            Dsl.process("P")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());
    var t = DescriptorFactory.fromTransaction(
            Dsl.transaction("T")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());
    var f = DescriptorFactory.fromFunction(
            Dsl.function("F").execute(ctx -> Result.success("ok")).build());

    assertThatCode(
            () -> SemanticValidator.validate(List.of(p), List.of(t), List.of(f), emptyRegistry))
            .doesNotThrowAnyException();
  }

  @Test
  void duplicateNameThrows() {
    var p1 = DescriptorFactory.fromProcess(
            Dsl.process("Dup")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());
    var p2 = DescriptorFactory.fromProcess(
            Dsl.process("Dup")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());

    var ex = catchThrowableOfType(
            ValidationException.class,
            () -> SemanticValidator.validate(
                    List.of(p1, p2), List.of(), List.of(), emptyRegistry));
    assertThat(ex).isNotNull();
    assertThat(ex.errors()).anyMatch(e -> e.contains("Dup"));
  }

  @Test
  void unknownHelperRefThrows() {
    var p = new ProcessDescriptor(
            "P", "v1", "P-queue", String.class, String.class, false, List.of("unknownHelper"));

    var ex = catchThrowableOfType(
            ValidationException.class,
            () -> SemanticValidator.validate(
                    List.of(p), List.of(), List.of(), emptyRegistry));
    assertThat(ex).isNotNull();
    assertThat(ex.errors()).anyMatch(e -> e.contains("unknownHelper"));
  }

  @Test
  void knownHelperRefPassesValidation() {
    var registry = new DefaultHelperRegistry();
    registry.registerHelper("myHelper", ctx -> Result.success("x"));

    var p = new ProcessDescriptor(
            "P", "v1", "P-queue", String.class, String.class, false, List.of("myHelper"));

    assertThatCode(
            () -> SemanticValidator.validate(List.of(p), List.of(), List.of(), registry))
            .doesNotThrowAnyException();
  }
}
