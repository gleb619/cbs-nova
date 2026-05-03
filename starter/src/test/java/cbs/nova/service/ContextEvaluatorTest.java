package cbs.nova.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cbs.dsl.api.EventDefinition;
import cbs.dsl.runtime.DslRegistry;
import cbs.nova.model.EventExecutionRequest;
import java.util.Map;
import kotlin.Unit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContextEvaluatorTest {

  @Mock
  private DslRegistry dslRegistry;

  @InjectMocks
  private ContextEvaluator contextEvaluator;

  @Test
  @DisplayName("shouldReturnMergedMapWhenContextBlockEnriches")
  @SuppressWarnings("unchecked")
  void shouldReturnMergedMapWhenContextBlockEnriches() {
    EventDefinition eventDef = mock(EventDefinition.class);
    EventExecutionRequest request =
        new EventExecutionRequest("loan", "submit", "user1", Map.of("amount", 5000));

    when(eventDef.getContextBlock()).thenReturn(ctx -> {
      ctx.set("enrichedField", "enrichedValue");
      return Unit.INSTANCE;
    });

    Map<String, Object> result = contextEvaluator.evaluate(eventDef, request);

    assertEquals(5000, result.get("amount"));
    assertEquals("enrichedValue", result.get("enrichedField"));
  }

  @Test
  @DisplayName("shouldLetEnrichmentWinOnKeyCollision")
  @SuppressWarnings("unchecked")
  void shouldLetEnrichmentWinOnKeyCollision() {
    EventDefinition eventDef = mock(EventDefinition.class);
    EventExecutionRequest request =
        new EventExecutionRequest("loan", "submit", "user1", Map.of("amount", 5000));

    when(eventDef.getContextBlock()).thenReturn(ctx -> {
      ctx.set("amount", 9999);
      return Unit.INSTANCE;
    });

    Map<String, Object> result = contextEvaluator.evaluate(eventDef, request);

    assertEquals(9999, result.get("amount"));
  }

  @Test
  @DisplayName("shouldPropagateExceptionWhenContextBlockThrows")
  void shouldPropagateExceptionWhenContextBlockThrows() {
    EventDefinition eventDef = mock(EventDefinition.class);
    EventExecutionRequest request = new EventExecutionRequest("loan", "submit", "user1", Map.of());

    when(eventDef.getContextBlock()).thenThrow(new RuntimeException("contextBlock error"));

    assertThrows(RuntimeException.class, () -> contextEvaluator.evaluate(eventDef, request));
  }
}
