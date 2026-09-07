package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import cbs.nova.starter.entity.DslAuditEntity;
import cbs.nova.starter.persistence.DslAuditRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.servlet.function.ServerRequest;

class DslAuditServiceTest {

  private final DslAuditRepository repository = mock(DslAuditRepository.class);
  private final DslAuditService service = new DslAuditService(repository,
          new tools.jackson.databind.ObjectMapper());

  @Test
  void recordPersistsRowWithSerializedDetails() {
    service.record("operator-1", "DEFINITION_PUBLISH", "LoanFlow", "corr-42",
            DslAuditService.OUTCOME_SUCCESS, Map.of("reloaded", true));

    var captor = ArgumentCaptor.forClass(DslAuditEntity.class);
    verify(repository).insert(captor.capture());
    DslAuditEntity row = captor.getValue();
    assertThat(row.id()).isNull();
    assertThat(row.occurredAt()).isNotNull();
    assertThat(row.actor()).isEqualTo("operator-1");
    assertThat(row.action()).isEqualTo("DEFINITION_PUBLISH");
    assertThat(row.target()).isEqualTo("LoanFlow");
    assertThat(row.correlationId()).isEqualTo("corr-42");
    assertThat(row.outcome()).isEqualTo("SUCCESS");
    assertThat(row.detailsJson()).isEqualTo("{\"reloaded\":true}");
  }

  @Test
  void recordStoresNullDetailsJsonWhenDetailsAbsent() {
    service.record("operator-1", "SCHEDULE_DELETE", "LoanFlow", null,
            DslAuditService.OUTCOME_SUCCESS, null);

    var captor = ArgumentCaptor.forClass(DslAuditEntity.class);
    verify(repository).insert(captor.capture());
    assertThat(captor.getValue().detailsJson()).isNull();
    assertThat(captor.getValue().correlationId()).isNull();
  }

  @Test
  void recordSwallowsRepositoryFailure() {
    doThrow(new DataAccessResourceFailureException("database is gone"))
            .when(repository).insert(any());

    assertThatCode(() -> service.record("operator-1", "DEFINITION_RELOAD", "/dsl",
            null, DslAuditService.OUTCOME_FAILURE, Map.of("error", "boom")))
            .doesNotThrowAnyException();
  }

  @Test
  void recordSwallowsSerializationFailure() {
    Map<String, Object> cyclic = new java.util.HashMap<>();
    cyclic.put("self", cyclic);

    assertThatCode(() -> service.record("operator-1", "DEFINITION_RELOAD", "/dsl",
            null, DslAuditService.OUTCOME_SUCCESS, cyclic))
            .doesNotThrowAnyException();
  }

  @Test
  void currentActorFallsBackToAnonymousWithoutSecurityContext() {
    assertThat(DslAuditService.currentActor()).isEqualTo("anonymous");
  }

  @Test
  void correlationIdOfReturnsNullWhenHeaderAbsent() {
    ServerRequest request = ServerRequest.create(
            new MockHttpServletRequest("GET", "/api/dsl/audit"), List.of());

    assertThat(DslAuditService.correlationIdOf(request)).isNull();
  }

  @Test
  void correlationIdOfReturnsValidatedHeaderValue() {
    var raw = new MockHttpServletRequest("GET", "/api/dsl/audit");
    raw.addHeader("X-Correlation-Id", "corr-abc_123");
    ServerRequest request = ServerRequest.create(raw, List.of());

    assertThat(DslAuditService.correlationIdOf(request)).isEqualTo("corr-abc_123");
  }

  @Test
  void correlationIdOfReturnsNullWhenHeaderInvalid() {
    var raw = new MockHttpServletRequest("GET", "/api/dsl/audit");
    raw.addHeader("X-Correlation-Id", "not a valid id!");
    ServerRequest request = ServerRequest.create(raw, List.of());

    assertThat(DslAuditService.correlationIdOf(request)).isNull();
  }
}
