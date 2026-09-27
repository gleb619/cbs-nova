package cbs.nova.starter.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.CompileDiagnosticDto;
import cbs.nova.starter.model.CompileDiagnosticRecord;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.persistence.CompileDiagnosticRecordRepository;
import cbs.nova.starter.persistence.CompileDiagnosticSearchResult;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.EntityResponse;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Unit tests for {@link DslDiagnosticsHandler}. Mirrors the direct {@link ServerRequest} /
 * {@link ServerResponse} style of {@link DslManifestHandlerTest}.
 */
class DslDiagnosticsHandlerTest {

  @Test
  void listUsesDefaultsAndReturnsPageResponse() {
    CompileDiagnosticRecordRepository repository = mock();
    CompileDiagnosticRecord record = diagnosticRecord();
    when(repository.search(isNull(), eq(0), eq(StarterConstants.DEFAULT_LIMIT)))
            .thenReturn(new CompileDiagnosticSearchResult(List.of(record), 1L));

    ServerResponse response = new DslDiagnosticsHandler(repository)
            .list(get("/api/dsl/diagnostics"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    assertThat(response.headers().getContentType()).isEqualTo(MediaType.APPLICATION_JSON);

    PageResponse<?> body = pageResponse(response);
    assertThat(body.total()).isEqualTo(1L);
    assertThat(body.offset()).isEqualTo(0);
    assertThat(body.limit()).isEqualTo(StarterConstants.DEFAULT_LIMIT);
    assertThat(body.items()).hasSize(1);
    assertThat(body.items().get(0)).isEqualTo(CompileDiagnosticDto.from(record));
  }

  @Test
  void listPassesDefinitionFilterWhenNonBlank() {
    CompileDiagnosticRecordRepository repository = mock();
    when(repository.search(eq("LoanDisbursement"), eq(0), eq(StarterConstants.DEFAULT_LIMIT)))
            .thenReturn(new CompileDiagnosticSearchResult(List.of(), 0L));

    ServerResponse response = new DslDiagnosticsHandler(repository)
            .list(get("/api/dsl/diagnostics", "definition", "LoanDisbursement"));

    assertThat(response.statusCode().value()).isEqualTo(200);
    PageResponse<?> body = pageResponse(response);
    assertThat(body.total()).isEqualTo(0L);
    assertThat(body.items()).isEmpty();
  }

  @Test
  void listPassesNullDefinitionWhenBlank() {
    CompileDiagnosticRecordRepository repository = mock();
    when(repository.search(isNull(), eq(0), eq(StarterConstants.DEFAULT_LIMIT)))
            .thenReturn(new CompileDiagnosticSearchResult(List.of(), 0L));

    ServerResponse response = new DslDiagnosticsHandler(repository)
            .list(get("/api/dsl/diagnostics", "definition", "   "));

    assertThat(response.statusCode().value()).isEqualTo(200);
    verify(repository).search(isNull(), eq(0), eq(StarterConstants.DEFAULT_LIMIT));
    assertThat(pageResponse(response).items()).isEmpty();
  }

  @Test
  void listOmitsDefinitionParameterWhenAbsent() {
    CompileDiagnosticRecordRepository repository = mock();
    when(repository.search(isNull(), eq(0), eq(StarterConstants.DEFAULT_LIMIT)))
            .thenReturn(new CompileDiagnosticSearchResult(List.of(), 0L));

    new DslDiagnosticsHandler(repository).list(get("/api/dsl/diagnostics"));

    verify(repository).search(isNull(), eq(0), eq(StarterConstants.DEFAULT_LIMIT));
  }

  @Test
  void listAppliesCustomLimitAndOffset() {
    CompileDiagnosticRecordRepository repository = mock();
    when(repository.search(isNull(), eq(7), eq(3)))
            .thenReturn(new CompileDiagnosticSearchResult(List.of(), 42L));

    ServerResponse response = new DslDiagnosticsHandler(repository)
            .list(get("/api/dsl/diagnostics", "limit", "3", "offset", "7"));

    PageResponse<?> body = pageResponse(response);
    assertThat(body.total()).isEqualTo(42L);
    assertThat(body.offset()).isEqualTo(7);
    assertThat(body.limit()).isEqualTo(3);
  }

  @Test
  void listClampsLimitToMaxAndOffsetToZero() {
    CompileDiagnosticRecordRepository repository = mock();
    when(repository.search(isNull(), eq(5), eq(StarterConstants.MAX_LIMIT)))
            .thenReturn(new CompileDiagnosticSearchResult(List.of(), 99L));

    ServerResponse response = new DslDiagnosticsHandler(repository)
            .list(get("/api/dsl/diagnostics", "limit", "99999", "offset", "5"));

    PageResponse<?> body = pageResponse(response);
    assertThat(body.total()).isEqualTo(99L);
    assertThat(body.offset()).isEqualTo(5);
    assertThat(body.limit()).isEqualTo(StarterConstants.MAX_LIMIT);
  }

  @Test
  void listClampsNegativeOffsetAndZeroLimit() {
    CompileDiagnosticRecordRepository repository = mock();
    when(repository.search(isNull(), eq(0), eq(1)))
            .thenReturn(new CompileDiagnosticSearchResult(List.of(), 0L));

    ServerResponse response = new DslDiagnosticsHandler(repository)
            .list(get("/api/dsl/diagnostics", "limit", "0", "offset", "-3"));

    PageResponse<?> body = pageResponse(response);
    assertThat(body.offset()).isEqualTo(0);
    assertThat(body.limit()).isEqualTo(1);
    assertThat(body.total()).isEqualTo(0L);
  }

  private static CompileDiagnosticRecord diagnosticRecord() {
    return new CompileDiagnosticRecord(
            1L,
            Instant.parse("2026-09-27T00:00:00Z"),
            "JAVA_COMPILER",
            "LoanDisbursement",
            "Loan.java",
            10L,
            5L,
            "ERROR",
            "DSL-1001",
            "unexpected token");
  }

  private static ServerRequest get(String path, String... params) {
    int q = path.indexOf('?');
    String requestPath = q < 0 ? path : path.substring(0, q);
    MockHttpServletRequest servlet = new MockHttpServletRequest("GET", requestPath);
    for (int i = 0; i < params.length; i += 2) {
      servlet.addParameter(params[i], params[i + 1]);
    }
    return ServerRequest.create(servlet, List.of());
  }

  @SuppressWarnings("unchecked")
  private static PageResponse<?> pageResponse(ServerResponse response) {
    assertThat(response).isInstanceOf(EntityResponse.class);
    return (PageResponse<?>) ((EntityResponse<?>) response).entity();
  }
}
