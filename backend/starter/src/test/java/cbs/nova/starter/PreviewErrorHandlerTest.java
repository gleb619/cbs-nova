package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.exception.DslCompensationException;
import cbs.nova.dsl.exception.DslEntityNotFoundException;
import cbs.nova.dsl.exception.DslValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import java.sql.SQLException;
import java.util.concurrent.TimeoutException;

class PreviewErrorHandlerTest {

  @Test
  void noSuchBeanDefinitionExceptionMapsToHelperNotFound() {
    var ex = new NoSuchBeanDefinitionException("MyHelper");

    PreviewErrorDetail detail = PreviewErrorHandler.from(ex, "MyHelper");

    assertThat(detail.code()).isEqualTo(PreviewErrorCode.HELPER_NOT_FOUND);
    assertThat(detail.message()).contains("MyHelper");
    assertThat(detail.suggestion()).containsIgnoringCase("register");
    assertThat(detail.context()).containsEntry("name", "MyHelper");
  }

  @Test
  void sqlExceptionMapsToExternalCallFailedWithSqlInContext() {
    var ex = new SQLException("syntax error at or near \"FROM\"", "42601", 1001);

    PreviewErrorDetail detail = PreviewErrorHandler.from(ex, "ChargeCard");

    assertThat(detail.code()).isEqualTo(PreviewErrorCode.EXTERNAL_CALL_FAILED);
    assertThat(detail.context()).containsEntry("sql", "syntax error at or near \"FROM\"");
    assertThat(detail.context()).containsEntry("sqlState", "42601");
    assertThat(detail.context()).containsEntry("errorCode", 1001);
    assertThat(detail.context()).containsEntry("name", "ChargeCard");
    assertThat(detail.suggestion()).containsIgnoringCase("sql");
  }

  @Test
  void classCastExceptionMapsToInputValidationErrorWithTypeMismatchDetails() {
    var ex = new ClassCastException(
            "class java.lang.Integer cannot be cast to class java.lang.String");

    PreviewErrorDetail detail = PreviewErrorHandler.from(ex, "CustomerLookup");

    assertThat(detail.code()).isEqualTo(PreviewErrorCode.INPUT_VALIDATION_ERROR);
    assertThat(detail.message()).contains("Integer", "String");
    assertThat(detail.context()).containsEntry("exceptionType",
            "java.lang.ClassCastException");
    assertThat(detail.context()).containsEntry("name", "CustomerLookup");
    assertThat(detail.suggestion()).containsIgnoringCase("input");
  }

  @Test
  void timeoutExceptionMapsToTimeoutExceededWithSuggestion() {
    var ex = new TimeoutException("preview exceeded 5s budget");

    PreviewErrorDetail detail = PreviewErrorHandler.from(ex, "LongRunning");

    assertThat(detail.code()).isEqualTo(PreviewErrorCode.TIMEOUT_EXCEEDED);
    assertThat(detail.message()).contains("5s budget");
    assertThat(detail.suggestion()).containsIgnoringCase("timeout");
    assertThat(detail.context()).containsEntry("name", "LongRunning");
  }

  @Test
  void genericRuntimeExceptionMapsToUnknownError() {
    var ex = new RuntimeException("something exploded");

    PreviewErrorDetail detail = PreviewErrorHandler.from(ex, "MysteryProcess");

    assertThat(detail.code()).isEqualTo(PreviewErrorCode.UNKNOWN_ERROR);
    assertThat(detail.message()).isEqualTo("something exploded");
    assertThat(detail.context()).containsEntry("exceptionType",
            "java.lang.RuntimeException");
    assertThat(detail.context()).containsEntry("name", "MysteryProcess");
    assertThat(detail.suggestion()).isNotBlank();
  }

  @Test
  void nullCauseProducesUnknownError() {
    PreviewErrorDetail detail = PreviewErrorHandler.from(null, "X");
    assertThat(detail.code()).isEqualTo(PreviewErrorCode.UNKNOWN_ERROR);
    assertThat(detail.context()).containsEntry("name", "X");
  }

  @Test
  void dslValidationExceptionMapsToDslCompilationError() {
    var ex = new DslValidationException("run-1", "missing required field");

    PreviewErrorDetail detail = PreviewErrorHandler.from(ex, "BadProcess");

    assertThat(detail.code()).isEqualTo(PreviewErrorCode.DSL_COMPILATION_ERROR);
    assertThat(detail.message()).isEqualTo("missing required field");
    assertThat(detail.context()).containsEntry("runId", "run-1");
    assertThat(detail.context()).containsEntry("name", "BadProcess");
  }

  @Test
  void dslCompensationExceptionMapsToCompensationError() {
    var ex = new DslCompensationException("run-2", "saga rollback failed", null);

    PreviewErrorDetail detail = PreviewErrorHandler.from(ex, "RefundFlow");

    assertThat(detail.code()).isEqualTo(PreviewErrorCode.COMPENSATION_ERROR);
    assertThat(detail.message()).contains("saga rollback failed");
    assertThat(detail.context()).containsEntry("runId", "run-2");
    assertThat(detail.context()).containsEntry("name", "RefundFlow");
  }

  @Test
  void dslEntityNotFoundHelperPrefixMapsToHelperNotFound() {
    var ex = new DslEntityNotFoundException("run-3", "Helper not found: MyMissingHelper");

    PreviewErrorDetail detail = PreviewErrorHandler.from(ex, "MyMissingHelper");

    assertThat(detail.code()).isEqualTo(PreviewErrorCode.HELPER_NOT_FOUND);
    assertThat(detail.context()).containsEntry("name", "MyMissingHelper");
    assertThat(detail.suggestion()).containsIgnoringCase("register");
  }

  @Test
  void illegalArgumentExceptionUnknownEntityPrefixMapsToHelperNotFound() {
    var ex = new IllegalArgumentException("No DSL entity registered: LostEntity");

    PreviewErrorDetail detail = PreviewErrorHandler.from(ex, "LostEntity");

    assertThat(detail.code()).isEqualTo(PreviewErrorCode.HELPER_NOT_FOUND);
    assertThat(detail.context()).containsEntry("name", "LostEntity");
    assertThat(detail.suggestion()).containsIgnoringCase("register");
  }

  @Test
  void fromOverloadWithoutEntityNameStillProducesCode() {
    var ex = new TimeoutException("timed out");

    PreviewErrorDetail detail = PreviewErrorHandler.from(ex);

    assertThat(detail.code()).isEqualTo(PreviewErrorCode.TIMEOUT_EXCEEDED);
    assertThat(detail.context()).doesNotContainKey("name");
  }
}
