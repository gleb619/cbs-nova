package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DslAuditRetentionPropertiesTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void defaultsAreOptOut() {
    DslAuditRetentionProperties props = new DslAuditRetentionProperties();

    assertThat(props.isEnabled()).isFalse();
    assertThat(props.getMaxAge()).isEqualTo(Duration.ofDays(90));
    assertThat(props.getBatchSize()).isEqualTo(500);
  }

  @Test
  void zeroBatchSizeFailsValidation() {
    DslAuditRetentionProperties props = new DslAuditRetentionProperties();
    props.setMaxAge(Duration.ofDays(30));
    props.setBatchSize(0);

    Set<ConstraintViolation<DslAuditRetentionProperties>> violations = validator.validate(props);
    assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("batchSize");
  }

  @Test
  void negativeBatchSizeFailsValidation() {
    DslAuditRetentionProperties props = new DslAuditRetentionProperties();
    props.setMaxAge(Duration.ofDays(30));
    props.setBatchSize(-5);

    Set<ConstraintViolation<DslAuditRetentionProperties>> violations = validator.validate(props);
    assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("batchSize");
  }

  @Test
  void positiveMaxAgeAndBatchSizePassValidation() {
    DslAuditRetentionProperties props = new DslAuditRetentionProperties();
    props.setEnabled(true);
    props.setMaxAge(Duration.ofDays(30));
    props.setBatchSize(250);

    Set<ConstraintViolation<DslAuditRetentionProperties>> violations = validator.validate(props);
    assertThat(violations).isEmpty();
  }
}
