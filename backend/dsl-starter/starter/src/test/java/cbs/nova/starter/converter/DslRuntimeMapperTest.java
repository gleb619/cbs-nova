package cbs.nova.starter.converter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.DslErrorCode;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.PreviewErrorDetail;
import cbs.nova.dsl.PreviewReport;
import cbs.nova.dsl.exception.DslException;
import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.ErrorResponseContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class DslRuntimeMapperTest {

  private final DslRuntimeMapper mapper = Mappers.getMapper(DslRuntimeMapper.class);

  @Test
  void mapsContextToErrorResponse() {
    ErrorResponse response = mapper.toErrorResponse(
            new ErrorResponseContext("CODE", "msg", "entity", "run-1", "run-1:ex:abc"));

    assertThat(response.code()).isEqualTo("CODE");
    assertThat(response.message()).isEqualTo("msg");
    assertThat(response.entityName()).isEqualTo("entity");
    assertThat(response.runId()).isEqualTo("run-1");
    assertThat(response.exceptionId()).isEqualTo("run-1:ex:abc");
  }

  @Test
  void buildsContextFromDslExceptionWithItsOwnFields() {
    DslException cause = new DslException("run-7", DslErrorCode.ENTITY_NOT_FOUND, "missing");

    ErrorResponseContext ctx = mapper.fromDslException(cause, "OrderFlow");

    ErrorResponse response = mapper.toErrorResponse(ctx);

    assertThat(response.code()).isEqualTo("ENTITY_NOT_FOUND");
    assertThat(response.message()).isEqualTo("missing");
    assertThat(response.entityName()).isEqualTo("OrderFlow");
    assertThat(response.runId()).isEqualTo("run-7");
    assertThat(response.exceptionId()).startsWith("run-7:ex:");
  }

  @Test
  void buildsContextFromGenericThrowableFallingBackToClassName() {
    Throwable withMessage = new IllegalStateException("kaboom");
    Throwable withoutMessage = new RuntimeException();

    ErrorResponseContext with = mapper.fromThrowable("Process", "run-1", withMessage);
    ErrorResponseContext without = mapper.fromThrowable("Process", "run-2", withoutMessage);

    assertThat(with.message()).isEqualTo("kaboom");
    assertThat(with.code()).isEqualTo("EXECUTION_FAILED");
    assertThat(with.exceptionId()).startsWith("run-1:ex:");

    assertThat(without.message()).isEqualTo("RuntimeException");
    assertThat(without.exceptionId()).startsWith("run-2:ex:");
  }

  @Test
  void buildsContextFromPreviewReportUsingFirstError() {
    PreviewReport report = new PreviewReport(
            "Ping",
            ExecutionMode.PREVIEW,
            false,
            null,
            List.of(),
            List.of(),
            Map.of(),
            null,
            List.of(),
            null,
            List.of(new PreviewErrorDetail(PreviewErrorCode.UNKNOWN_ERROR, "boom", "fix", Map.of())));

    ErrorResponseContext ctx = mapper.fromPreviewReport("Ping", "run-3", report);
    ErrorResponse response = mapper.toErrorResponse(ctx);

    assertThat(response.code()).isEqualTo("UNKNOWN_ERROR");
    assertThat(response.message()).isEqualTo("boom");
    assertThat(response.entityName()).isEqualTo("Ping");
    assertThat(response.runId()).isEqualTo("run-3");
    assertThat(response.exceptionId()).startsWith("run-3:ex:");
  }

  @Test
  void buildsContextFromPreviewReportFallingBackWhenNoErrors() {
    PreviewReport report = new PreviewReport(
            "Ping",
            ExecutionMode.PREVIEW,
            false,
            null,
            List.of(),
            List.of(),
            Map.of(),
            null,
            List.of(),
            null,
            List.of());

    ErrorResponseContext ctx = mapper.fromPreviewReport("Ping", "run-4", report);

    assertThat(ctx.code()).isEqualTo("EXECUTION_FAILED");
    assertThat(ctx.message()).isEqualTo("Preview failed");
    assertThat(ctx.exceptionId()).startsWith("run-4:ex:");
  }

  @Test
  void buildsContextFromNullPreviewReport() {
    ErrorResponseContext ctx = mapper.fromPreviewReport("Ping", "run-5", null);

    assertThat(ctx.code()).isEqualTo("EXECUTION_FAILED");
    assertThat(ctx.message()).isEqualTo("Preview failed");
  }
}