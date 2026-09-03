package cbs.nova.starter.helper;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.helper.model.ValidateJsonIn;
import cbs.nova.starter.helper.model.ValidateJsonOut;
import cbs.nova.starter.model.ValidationError;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidateJsonHelperTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ValidateJsonHelper helper = new ValidateJsonHelper();

  @Test
  void validObjectPayloadAndSchema() {
    String payload = "{\"name\":\"Alice\",\"age\":30}";
    String schema = "{\"type\":\"object\",\"required\":[\"name\",\"age\"],\"properties\":{\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"number\"}}}";
    Result<ValidateJsonOut> result = execute(new ValidateJsonIn(payload, schema));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().errors()).isEmpty();
    assertThat(result.value().valid()).isTrue();
  }

  @Test
  void missingRequiredFieldReportsOneError() {
    String payload = "{\"name\":\"Alice\"}";
    String schema = "{\"type\":\"object\",\"required\":[\"name\",\"age\"],\"properties\":{\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"number\"}}}";
    Result<ValidateJsonOut> result = execute(new ValidateJsonIn(payload, schema));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().valid()).isFalse();
    assertThat(result.value().errors()).hasSize(1);
    ValidationError error = result.value().errors().get(0);
    assertThat(error.field()).isEqualTo("$.age");
    assertThat(error.message()).isEqualTo("field is required");
  }

  @Test
  void nestedObjectTypeMismatchReportsPath() {
    String payload = "{\"address\":{\"zip\":12345}}";
    String schema = "{\"type\":\"object\",\"properties\":{\"address\":{\"type\":\"object\",\"properties\":{\"zip\":{\"type\":\"string\"}}}}}";
    Result<ValidateJsonOut> result = execute(new ValidateJsonIn(payload, schema));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().valid()).isFalse();
    assertThat(result.value().errors()).hasSize(1);
    assertThat(result.value().errors().get(0).field()).isEqualTo("$.address.zip");
  }

  @Test
  void arrayElementErrorReportsPath() {
    String payload = "[{\"code\":\"ABC\"},{\"code\":123}]";
    String schema = "{\"type\":\"array\",\"items\":{\"type\":\"object\",\"properties\":{\"code\":{\"type\":\"string\"}}}}";
    Result<ValidateJsonOut> result = execute(new ValidateJsonIn(payload, schema));
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value().valid()).isFalse();
    List<ValidationError> errors = result.value().errors();
    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).field()).isEqualTo("$.items[1].code");
  }

  @Test
  void malformedSchemaStringFails() {
    String payload = "{\"name\":\"Alice\"}";
    String schema = "not valid json";
    Result<ValidateJsonOut> result = execute(new ValidateJsonIn(payload, schema));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("validateJson: invalid");
  }

  @Test
  void emptyPayloadFails() {
    Result<ValidateJsonOut> result = execute(new ValidateJsonIn("", "{\"type\":\"object\"}"));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("validateJson.payload is required");
  }

  @Test
  void emptySchemaFails() {
    Result<ValidateJsonOut> result = execute(new ValidateJsonIn("{}", ""));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("validateJson.schema is required");
  }

  private Result<ValidateJsonOut> execute(ValidateJsonIn input) {
    var ctx = contextFactory.of(input, ExecutionMode.PREVIEW);
    return helper.execute(ctx);
  }
}
