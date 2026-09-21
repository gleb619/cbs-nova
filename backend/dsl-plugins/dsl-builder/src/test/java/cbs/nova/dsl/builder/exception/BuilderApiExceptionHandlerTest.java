package cbs.nova.dsl.builder.exception;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.BuilderErrorResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;

class BuilderApiExceptionHandlerTest {

  private final BuilderApiExceptionHandler handler = new BuilderApiExceptionHandler();

  @Test
  void handleApiReturnsStatusAndCodeFromException() {
    var ex = new BuilderApiException(HttpStatus.NOT_FOUND, "DSL_NOT_FOUND", "not found");

    var response = handler.handleApi(ex);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(response.getBody()).extracting(BuilderErrorResponse::code)
            .isEqualTo("DSL_NOT_FOUND");
    assertThat(response.getBody()).extracting(BuilderErrorResponse::message).isEqualTo("not found");
  }

  @Test
  void handleApiReturnsBodyWithNullDiagnostics() {
    var ex = new BuilderApiException(HttpStatus.CONFLICT, "CONFLICT", "msg");

    var response = handler.handleApi(ex);

    assertThat(response.getBody().diagnostics()).isEmpty();
  }

  @Test
  void handleBusyReturns429() {
    var ex = new BuilderBusyException("queue full");

    BuilderErrorResponse response = handler.handleBusy(ex);

    assertThat(response.code()).isEqualTo("BUILDER_BUSY");
    assertThat(response.message()).isEqualTo("queue full");
  }

  @Test
  void handleBadRequestReturns400() {
    var ex = new IllegalArgumentException("bad param");

    BuilderErrorResponse response = handler.handleBadRequest(ex);

    assertThat(response.code()).isEqualTo("INVALID_REQUEST");
    assertThat(response.message()).isEqualTo("bad param");
  }

  @Test
  void handleUnreadableBodyReturns400WithFixedMessage() {
    var ex = new HttpMessageNotReadableException("Unexpected character", null, null);

    BuilderErrorResponse response = handler.handleUnreadableBody(ex);

    assertThat(response.code()).isEqualTo("INVALID_REQUEST");
    assertThat(response.message()).isEqualTo("malformed request body");
  }

  @Test
  void handleBulkheadReturns503() {
    var ex = new IllegalStateException("bulkhead saturated");

    BuilderErrorResponse response = handler.handleBulkhead(ex);

    assertThat(response.code()).isEqualTo("BULKHEAD_SATURATED");
    assertThat(response.message()).isEqualTo("bulkhead saturated");
  }

  @Test
  void messageOfFallsBackToSimpleClassNameWhenNull() {
    var ex = new BuilderBusyException(null);

    BuilderErrorResponse response = handler.handleBusy(ex);

    assertThat(response.message()).isEqualTo("BuilderBusyException");
  }

  @Test
  void messageOfFallsBackForNullMessageIllegalArgumentException() {
    var ex = new IllegalArgumentException((String) null);

    BuilderErrorResponse response = handler.handleBadRequest(ex);

    assertThat(response.message()).isEqualTo("IllegalArgumentException");
  }

  @Test
  void handleUnreadableBodyDiscardsOriginalMessage() {
    var ex = new HttpMessageNotReadableException("original parse error detail", null, null);

    BuilderErrorResponse response = handler.handleUnreadableBody(ex);

    assertThat(response.message()).isEqualTo("malformed request body");
    assertThat(response.message()).isNotEqualTo("original parse error detail");
  }
}
