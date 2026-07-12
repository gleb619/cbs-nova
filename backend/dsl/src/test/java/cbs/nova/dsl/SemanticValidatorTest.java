package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import cbs.nova.dsl.config.DescriptorFactory;
import cbs.nova.dsl.process.ProcessDescriptor;
import cbs.nova.dsl.registry.DefaultHelperRegistry;
import cbs.nova.dsl.registry.HelperRegistry;
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
  void duplicateNameThrows() {
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
  }

  @Test
  void unknownHelperRefThrows() {
    var p = new ProcessDescriptor(
            "P", "v1", "P-queue", String.class, String.class, false, List.of("unknownHelper"));

    var ex = catchThrowableOfType(
            ValidationException.class,
            () -> new SemanticValidator().validate(
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
            () -> new SemanticValidator().validate(List.of(p), List.of(), List.of(), registry))
            .doesNotThrowAnyException();
  }
}
