package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DslNotificationFiringRetentionPropertiesTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void defaultsAreOptOut() {
    DslNotificationFiringRetentionProperties props = new DslNotificationFiringRetentionProperties();

    assertThat(props.isEnabled()).isFalse();
    assertThat(props.getMaxAge()).isEqualTo(Duration.ofDays(90));
    assertThat(props.getBatchSize()).isEqualTo(500);
  }

  @Test
  void zeroBatchSizeFailsValidation() {
    DslNotificationFiringRetentionProperties props = new DslNotificationFiringRetentionProperties();
    props.setMaxAge(Duration.ofDays(30));
    props.setBatchSize(0);

    Set<ConstraintViolation<DslNotificationFiringRetentionProperties>> violations = validator
            .validate(props);
    assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("batchSize");
  }

  @Test
  void negativeBatchSizeFailsValidation() {
    DslNotificationFiringRetentionProperties props = new DslNotificationFiringRetentionProperties();
    props.setMaxAge(Duration.ofDays(30));
    props.setBatchSize(-5);

    Set<ConstraintViolation<DslNotificationFiringRetentionProperties>> violations = validator
            .validate(props);
    assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("batchSize");
  }

  @Test
  void positiveMaxAgeAndBatchSizePassValidation() {
    DslNotificationFiringRetentionProperties props = new DslNotificationFiringRetentionProperties();
    props.setEnabled(true);
    props.setMaxAge(Duration.ofDays(30));
    props.setBatchSize(250);

    Set<ConstraintViolation<DslNotificationFiringRetentionProperties>> violations = validator
            .validate(props);
    assertThat(violations).isEmpty();
  }
}
