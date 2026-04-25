package cbs.app.temporal.activity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransactionInput;
import cbs.dsl.runtime.DslRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

class TransactionActivityImplTest {

  private final DslRegistry dslRegistry = mock(DslRegistry.class);
  private final TransactionActivityImpl activity =
      new TransactionActivityImpl(dslRegistry, new ObjectMapper());

  @Test
  @DisplayName("Should call preview and execute and return success when transaction succeeds")
  void shouldCallPreviewAndExecuteAndReturnSuccessWhenTransactionSucceeds() {
    // Arrange
    TransactionDefinition txDef = mock(TransactionDefinition.class);
    when(txDef.getCode()).thenReturn("SUCCESS_TX");

    when(dslRegistry.getTransactions()).thenReturn(Map.of("SUCCESS_TX", txDef));

    TransactionActivityInput input =
        new TransactionActivityInput("SUCCESS_TX", "{}", 1L, "testUser", "1.0.0");

    // Act
    TransactionResult result = activity.executeTransaction(input);

    // Assert
    assertTrue(result.success());
    verify(txDef, times(1)).preview(any(TransactionInput.class));
    verify(txDef, times(1)).execute(any(TransactionInput.class));
  }

  @Test
  @DisplayName("Should call rollback and return failure when execute throws")
  void shouldCallRollbackAndReturnFailureWhenExecuteThrows() {
    // Arrange
    TransactionDefinition txDef = mock(TransactionDefinition.class);
    when(txDef.getCode()).thenReturn("FAIL_TX");
    doThrow(new RuntimeException("Execute failed"))
        .when(txDef)
        .execute(any(TransactionInput.class));

    when(dslRegistry.getTransactions()).thenReturn(Map.of("FAIL_TX", txDef));

    TransactionActivityInput input =
        new TransactionActivityInput("FAIL_TX", "{}", 1L, "testUser", "1.0.0");

    // Act
    TransactionResult result = activity.executeTransaction(input);

    // Assert
    assertFalse(result.success());
    assertEquals("Execute failed", result.errorMessage());
    verify(txDef, times(1)).preview(any(TransactionInput.class));
    verify(txDef, times(1)).execute(any(TransactionInput.class));
    verify(txDef, times(1)).rollback(any(TransactionInput.class));
  }

  @Test
  @DisplayName("Should return failure when transaction code is not found")
  void shouldReturnFailureWhenTransactionCodeIsNotFound() {
    // Arrange
    when(dslRegistry.getTransactions()).thenReturn(Map.of());

    TransactionActivityInput input =
        new TransactionActivityInput("UNKNOWN_TX", "{}", 1L, "testUser", "1.0.0");

    // Act
    TransactionResult result = activity.executeTransaction(input);

    // Assert
    assertFalse(result.success());
    assertEquals("Transaction not found: UNKNOWN_TX", result.errorMessage());
  }
}
