package cbs.nova.starter.config.properties;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.time.Duration;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DslWebhookDeliveryRetentionPropertiesTest {

  private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

  @Test
  void defaultsAreOptOut() {
    DslWebhookDeliveryRetentionProperties props = new DslWebhookDeliveryRetentionProperties();

    assertThat(props.isEnabled()).isFalse();
    assertThat(props.getMaxAge()).isEqualTo(Duration.ofDays(30));
    assertThat(props.getBatchSize()).isEqualTo(500);
  }

  @Test
  void zeroBatchSizeFailsValidation() {
    DslWebhookDeliveryRetentionProperties props = new DslWebhookDeliveryRetentionProperties();
    props.setMaxAge(Duration.ofDays(7));
    props.setBatchSize(0);

    Set<ConstraintViolation<DslWebhookDeliveryRetentionProperties>> violations = validator
            .validate(props);
    assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("batchSize");
  }

  @Test
  void negativeBatchSizeFailsValidation() {
    DslWebhookDeliveryRetentionProperties props = new DslWebhookDeliveryRetentionProperties();
    props.setMaxAge(Duration.ofDays(7));
    props.setBatchSize(-5);

    Set<ConstraintViolation<DslWebhookDeliveryRetentionProperties>> violations = validator
            .validate(props);
    assertThat(violations).extracting(v -> v.getPropertyPath().toString()).contains("batchSize");
  }

  @Test
  void positiveMaxAgeAndBatchSizePassValidation() {
    DslWebhookDeliveryRetentionProperties props = new DslWebhookDeliveryRetentionProperties();
    props.setEnabled(true);
    props.setMaxAge(Duration.ofDays(14));
    props.setBatchSize(250);

    Set<ConstraintViolation<DslWebhookDeliveryRetentionProperties>> violations = validator
            .validate(props);
    assertThat(violations).isEmpty();
  }
}
