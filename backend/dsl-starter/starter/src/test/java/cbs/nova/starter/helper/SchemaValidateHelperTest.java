package cbs.nova.starter.helper;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.starter.helper.model.SchemaValidateIn;
import cbs.nova.starter.helper.model.SchemaValidateOut;
import cbs.nova.starter.model.ValidationError;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SchemaValidateHelperTest {

  private final SchemaValidateHelper helper = new SchemaValidateHelper();

  @Test
  void validPayloadAgainstSchemaReturnsValidTrueAndSummary() {
    String payload = "{\"name\":\"Alice\",\"age\":30,\"address\":{\"zip\":\"12345\"}}";
    String schema = "{\"type\":\"object\",\"required\":[\"name\",\"age\"],"
            + "\"properties\":{\"name\":{\"type\":\"string\"},"
            + "\"age\":{\"type\":\"number\"},"
            + "\"address\":{\"type\":\"object\","
            + "\"properties\":{\"zip\":{\"type\":\"string\"}}}}}";

    Result<SchemaValidateOut> result = execute(new SchemaValidateIn(payload, schema, null));

    assertThat(result.isSuccess()).isTrue();
    SchemaValidateOut out = result.value();
    assertThat(out.valid()).isTrue();
    assertThat(out.errors()).isEmpty();
    assertThat(out.summary()).isEqualTo("valid");
  }

  @Test
  void invalidPayloadReportsAllNestedErrorsByDefault() {
    String payload = "{\"name\":42,\"address\":{\"zip\":12345,\"city\":null},"
            + "\"items\":[{\"code\":\"ABC\"},{\"code\":123}]}";
    String schema = "{\"type\":\"object\",\"required\":[\"name\",\"age\"],"
            + "\"properties\":{\"name\":{\"type\":\"string\"},"
            + "\"age\":{\"type\":\"number\"},"
            + "\"address\":{\"type\":\"object\",\"required\":[\"city\"],"
            + "\"properties\":{\"zip\":{\"type\":\"string\"},"
            + "\"city\":{\"type\":\"string\"}}},"
            + "\"items\":{\"type\":\"array\","
            + "\"items\":{\"type\":\"object\","
            + "\"properties\":{\"code\":{\"type\":\"string\"}}}}}}";

    Result<SchemaValidateOut> result = execute(new SchemaValidateIn(payload, schema, null));

    assertThat(result.isSuccess()).isTrue();
    SchemaValidateOut out = result.value();
    assertThat(out.valid()).isFalse();
    List<ValidationError> errors = out.errors();
    // name wrong type, age missing, city null, items[1].code wrong type
    assertThat(errors).hasSizeGreaterThanOrEqualTo(4);
    assertThat(errors.stream().map(ValidationError::field))
            .contains("$.name", "$.age", "$.address.city", "$.address.zip",
                    "$.items.items[1].code");
    assertThat(out.summary()).startsWith("invalid: ").endsWith("error(s)");
  }

  @Test
  void failFastTruncatesToFirstError() {
    String payload = "{\"name\":42,\"age\":\"not-a-number\"}";
    String schema = "{\"type\":\"object\",\"properties\":"
            + "{\"name\":{\"type\":\"string\"},\"age\":{\"type\":\"number\"}}}";

    Result<SchemaValidateOut> result = execute(new SchemaValidateIn(payload, schema, true));

    assertThat(result.isSuccess()).isTrue();
    SchemaValidateOut out = result.value();
    assertThat(out.valid()).isFalse();
    assertThat(out.errors()).hasSize(1);
    assertThat(out.errors().get(0).field()).isEqualTo("$.name");
    assertThat(out.summary()).isEqualTo("invalid: 1 error(s)");
  }

  @Test
  void failFastDoesNotTruncateSingleError() {
    String payload = "{\"name\":42}";
    String schema = "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}}}";

    Result<SchemaValidateOut> result = execute(new SchemaValidateIn(payload, schema, true));

    assertThat(result.isSuccess()).isTrue();
    SchemaValidateOut out = result.value();
    assertThat(out.valid()).isFalse();
    assertThat(out.errors()).hasSize(1);
    assertThat(out.summary()).isEqualTo("invalid: 1 error(s)");
  }

  @Test
  void malformedSchemaFailsWithHelperLevelErrorEnvelope() {
    String payload = "{\"name\":\"Alice\"}";

    Result<SchemaValidateOut> result = execute(
            new SchemaValidateIn(payload, "not valid json", null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("schemaValidate: invalid payload or schema");
  }

  @Test
  void malformedPayloadFailsWithHelperLevelErrorEnvelope() {
    String schema = "{\"type\":\"object\"}";

    Result<SchemaValidateOut> result = execute(new SchemaValidateIn("{broken json", schema, null));

    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessageContaining("schemaValidate: invalid payload or schema");
  }

  @Test
  void emptyPayloadFails() {
    Result<SchemaValidateOut> result = execute(
            new SchemaValidateIn("", "{\"type\":\"object\"}", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("schemaValidate.payload is required");
  }

  @Test
  void blankSchemaFails() {
    Result<SchemaValidateOut> result = execute(new SchemaValidateIn("{}", "   ", null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("schemaValidate.schema is required");
  }

  @Test
  void nullSchemaFails() {
    Result<SchemaValidateOut> result = execute(new SchemaValidateIn("{}", null, null));
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(IllegalArgumentException.class);
    assertThat(result.cause()).hasMessage("schemaValidate.schema is required");
  }

  private Result<SchemaValidateOut> execute(SchemaValidateIn input) {
    var ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW).build();
    return helper.execute(ctx);
  }
}
