package cbs.nova.sample;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import cbs.dsl.api.ConditionDefinition;
import cbs.dsl.api.ConditionTypes.ConditionInput;
import cbs.dsl.api.ConditionTypes.ConditionOutput;
import cbs.dsl.api.HelperDefinition;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionTypes.TransactionInput;
import cbs.dsl.api.TransactionTypes.TransactionOutput;
import cbs.dsl.codegen.generated.definitions.SampleConditionDefinition;
import cbs.dsl.codegen.generated.definitions.SampleEventDefinition;
import cbs.dsl.codegen.generated.definitions.SampleHelperDefinition;
import cbs.dsl.codegen.generated.definitions.SampleTransactionDefinition;
import cbs.dsl.codegen.generated.definitions.SampleWorkflowDefinition;
import cbs.nova.registry.DslRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

/**
 * Layer 1 test: verifies that {@code @DslComponent} on {@code *Function} classes generates
 * {@code *Definition} wrappers that are correctly registered and delegate to the underlying
 * functions.
 */
class SampleWorkflowTest {

  @Test
  @DisplayName("Should resolve generated SampleTransactionDefinition and execute via wrapper")
  void shouldResolveGeneratedSampleTransactionDefinition() {
    // Arrange
    DslRegistry registry = new DslRegistry();
    registry.register(new SampleTransactionDefinition());
    registry.register(new SampleEventDefinition());
    registry.register(new SampleWorkflowDefinition());

    // Act
    TransactionDefinition tx = registry.resolveTransaction("SAMPLE_TX");
    TransactionOutput output = tx.execute(new TransactionInput(Map.of("name", "PoC")));

    // Assert
    assertNotNull(tx);
    assertEquals("SAMPLE_TX", tx.getCode());
    assertEquals("Hello, PoC", output.result().get("greeting"));
}

  @Test
  @DisplayName("Should resolve generated SampleHelperDefinition and execute via wrapper")
  void shouldResolveGeneratedSampleHelperDefinition() {
    // Arrange
    DslRegistry registry = new DslRegistry();
    registry.register(new SampleHelperDefinition());

    // Act
    HelperDefinition helper = registry.resolveHelper("SAMPLE_HELPER");
    HelperOutput output =
        helper.execute(new HelperInput(Map.of("someVal", "Hello, World"), null, null));

    // Assert
    assertNotNull(helper);
    assertEquals("SAMPLE_HELPER", helper.getCode());
    assertEquals("Hello, World!", ((Map<?, ?>) output.value()).get("result"));
}

  @Test
  @DisplayName("Should resolve generated SampleConditionDefinition and evaluate via wrapper")
  void shouldResolveGeneratedSampleConditionDefinition() {
    // Arrange
    DslRegistry registry = new DslRegistry();
    registry.register(new SampleConditionDefinition());

    // Act
    ConditionDefinition condition = registry.resolveCondition("SAMPLE_CONDITION");
    ConditionOutput output = condition.evaluate(new ConditionInput(Map.of(), null, null));

    // Assert
    assertNotNull(condition);
    assertEquals("SAMPLE_CONDITION", condition.getCode());
    assertTrue(output.result());
  }
}
