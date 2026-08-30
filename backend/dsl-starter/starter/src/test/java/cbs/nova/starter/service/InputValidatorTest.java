package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.ExecutableDescriptor;
import cbs.nova.dsl.JsonSchemaGenerator;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.ParameterDescriptor;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.jsonschema.JacksonJsonSchemaGenerator;
import cbs.nova.starter.config.properties.InputValidationProperties;
import cbs.nova.starter.model.ValidationError;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

class InputValidatorTest {

  private JsonSchemaGenerator generator;
  private InputValidator validator;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().helperInstanceResolver().replace(null);
    generator = spy(new JacksonJsonSchemaGenerator());
    validator = new InputValidator(
            generator,
            new InputValidationProperties(true),
            Caffeine.newBuilder().build());
  }

  @AfterEach
  void tearDown() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void validInputPasses() {
    registerProcess("ValidProcess", SampleInput.class);

    List<ValidationError> errors = validator.validate("ValidProcess",
            Map.of("name", "alice", "count", 1));

    assertThat(errors).isEmpty();
  }

  @Test
  void wrongTypeInputRejectedWithFieldPath() {
    registerProcess("TypedProcess", SampleInput.class);

    List<ValidationError> errors = validator.validate("TypedProcess",
            Map.of("name", 123, "count", 1));

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$.name");
    assertThat(errors.get(0).message()).isEqualTo("expected type string");
    assertThat(errors.get(0).severity()).isEqualTo("error");
  }

  @Test
  void missingRequiredFieldRejectedWithFieldPath() {
    registerProcess("RequiredProcess", SampleInput.class);

    List<ValidationError> errors = validator.validate("RequiredProcess",
            Map.of("count", 1));

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$.name");
    assertThat(errors.get(0).message()).isEqualTo("field is required");
  }

  @Test
  void constructResolutionWorksForProcessTransactionAndHelper() {
    registerProcess("Proc", SampleInput.class);
    registerTransaction("Tx", SampleInput.class);
    registerHelper("Hlp", SampleInput.class);

    assertThat(validator.validate("Proc", Map.of("name", "a", "count", 1))).isEmpty();
    assertThat(validator.validate("Tx", Map.of("name", "a", "count", 1))).isEmpty();
    assertThat(validator.validate("Hlp", Map.of("name", "a", "count", 1))).isEmpty();
  }

  @Test
  void nonRecordInputPassesWithoutShapeCheck() {
    registerProcess("StringProcess", String.class);

    List<ValidationError> errors = validator.validate("StringProcess", "hello");

    assertThat(errors).isEmpty();
  }

  @Test
  void schemaCacheReusesGeneratedSchema() {
    registerProcess("CachedProcess", SampleInput.class);

    validator.validate("CachedProcess", Map.of("name", "a", "count", 1));
    validator.validate("CachedProcess", Map.of("name", "b", "count", 2));

    verify(generator, times(1)).generateSchema(any(Class.class));
  }

  @Test
  void disabledValidatorReturnsEmptyList() {
    registerProcess("IgnoredProcess", SampleInput.class);
    InputValidator disabled = new InputValidator(
            generator,
            new InputValidationProperties(false),
            Caffeine.newBuilder().build());

    List<ValidationError> errors = disabled.validate("IgnoredProcess",
            Map.of("name", 123));

    assertThat(errors).isEmpty();
    verify(generator, times(0)).generateSchema(any(Class.class));
  }

  @Test
  void unknownConstructReturnsEmptyList() {
    List<ValidationError> errors = validator.validate("NoSuchProcess",
            Map.of("name", "a"));

    assertThat(errors).isEmpty();
  }

  private void registerProcess(String name, Class<?> inputType) {
    GlobalManager.globalManager().registerProcess(
            Dsl.process(name)
                    .input(inputType)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .build());
  }

  private void registerTransaction(String name, Class<?> inputType) {
    GlobalManager.globalManager().registerTransaction(
            Dsl.transaction(name)
                    .input(inputType)
                    .output(String.class)
                    .execute(ctx -> Result.success("ok"))
                    .startToCloseTimeout(Duration.ofMinutes(1))
                    .build());
  }

  private void registerHelper(String name, Class<?> inputType) {
    GlobalManager.globalManager().registerHelper(name, new Executable<Object, String>() {
      @Override
      public Result<String> execute(Context<Object> ctx) {
        return Result.success("ok");
      }

      @Override
      public ExecutableDescriptor describe() {
        return new ExecutableDescriptor(name, null, inputType, String.class, false,
                "delegates to execute", List.of(ParameterDescriptor.ofString("name")));
      }
    });
  }

  public record SampleInput(String name, Integer count) {
  }
}
