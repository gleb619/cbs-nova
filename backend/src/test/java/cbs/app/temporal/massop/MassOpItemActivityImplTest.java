package cbs.app.temporal.massop;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.SourceDefinition;
import cbs.dsl.api.context.MassOperationContext;
import cbs.dsl.runtime.DslRegistry;
import cbs.nova.repository.MassOperationExecutionRepository;
import cbs.nova.repository.MassOperationItemRepository;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

class MassOpItemActivityImplTest {

  private DslRegistry dslRegistry;
  private ObjectMapper objectMapper;
  private MassOpItemActivityImpl activity;

  @BeforeEach
  void setUp() {
    dslRegistry = mock(DslRegistry.class);
    objectMapper = new ObjectMapper();
    MassOperationExecutionRepository executionRepository =
        mock(MassOperationExecutionRepository.class);
    MassOperationItemRepository itemRepository = mock(MassOperationItemRepository.class);
    activity =
        new MassOpItemActivityImpl(dslRegistry, objectMapper, executionRepository, itemRepository);
  }

  @Test
  @DisplayName("should return success when itemBlock executes without error")
  void shouldReturnSuccessWhenItemBlockExecutesWithoutError() {
    // Given
    String massOpCode = "TEST_MASS_OP";
    @SuppressWarnings("unchecked")
    Function1<MassOperationContext, Unit> itemBlock =
        (Function1<MassOperationContext, Unit>) mock(Function1.class);
    when(itemBlock.invoke(any(MassOperationContext.class))).thenReturn(Unit.INSTANCE);

    MassOperationDefinition def = mock(MassOperationDefinition.class);
    when(def.getCode()).thenReturn(massOpCode);
    when(def.getItemBlock()).thenReturn(itemBlock);

    SourceDefinition source = mock(SourceDefinition.class);
    when(source.load(any(MassOperationContext.class))).thenReturn(List.of());
    when(def.getSource()).thenReturn(source);

    when(dslRegistry.getMassOperations()).thenReturn(Map.of(massOpCode, def));

    MassOpItemInput input =
        new MassOpItemInput("item1", "{\"key\":\"value\"}", massOpCode, 1L, "testUser", "1.0");

    // When
    MassOpItemResult result = activity.processItem(input);

    // Then
    assertTrue(result.success());
    assertNull(result.errorMessage());
    verify(itemBlock).invoke(any(MassOperationContext.class));
  }

  @Test
  @DisplayName("should return failure when itemBlock throws exception")
  void shouldReturnFailureWhenItemBlockThrowsException() {
    // Given
    String massOpCode = "TEST_MASS_OP_FAIL";
    @SuppressWarnings("unchecked")
    Function1<MassOperationContext, Unit> itemBlock =
        (Function1<MassOperationContext, Unit>) mock(Function1.class);
    when(itemBlock.invoke(any(MassOperationContext.class)))
        .thenThrow(new RuntimeException("item processing failed"));

    MassOperationDefinition def = mock(MassOperationDefinition.class);
    when(def.getCode()).thenReturn(massOpCode);
    when(def.getItemBlock()).thenReturn(itemBlock);

    SourceDefinition source = mock(SourceDefinition.class);
    when(source.load(any(MassOperationContext.class))).thenReturn(List.of());
    when(def.getSource()).thenReturn(source);

    when(dslRegistry.getMassOperations()).thenReturn(Map.of(massOpCode, def));

    MassOpItemInput input =
        new MassOpItemInput("item1", "{\"key\":\"value\"}", massOpCode, 1L, "testUser", "1.0");

    // When
    MassOpItemResult result = activity.processItem(input);

    // Then
    assertFalse(result.success());
    assertEquals("item processing failed", result.errorMessage());
  }

  @Test
  @DisplayName("should return failure when massOpCode is not found")
  void shouldReturnFailureWhenMassOpCodeNotFound() {
    // Given
    when(dslRegistry.getMassOperations()).thenReturn(Map.of());

    MassOpItemInput input =
        new MassOpItemInput("item1", "{\"key\":\"value\"}", "UNKNOWN_CODE", 1L, "testUser", "1.0");

    // When
    MassOpItemResult result = activity.processItem(input);

    // Then
    assertFalse(result.success());
    assertEquals("MassOp not found: UNKNOWN_CODE", result.errorMessage());
  }

  @Test
  @DisplayName("should return failure when itemDataJson is invalid JSON")
  void shouldReturnFailureWhenItemDataJsonIsInvalidJson() {
    // Given
    String massOpCode = "TEST_MASS_OP";
    @SuppressWarnings("unchecked")
    Function1<MassOperationContext, Unit> itemBlock =
        (Function1<MassOperationContext, Unit>) mock(Function1.class);

    MassOperationDefinition def = mock(MassOperationDefinition.class);
    when(def.getCode()).thenReturn(massOpCode);
    when(def.getItemBlock()).thenReturn(itemBlock);

    SourceDefinition source = mock(SourceDefinition.class);
    when(source.load(any(MassOperationContext.class))).thenReturn(List.of());
    when(def.getSource()).thenReturn(source);

    when(dslRegistry.getMassOperations()).thenReturn(Map.of(massOpCode, def));

    MassOpItemInput input =
        new MassOpItemInput("item1", "not-valid-json{{{", massOpCode, 1L, "testUser", "1.0");

    // When
    MassOpItemResult result = activity.processItem(input);

    // Then
    assertFalse(result.success());
    assertTrue(result.errorMessage().contains("Invalid item data JSON"));
  }
}
