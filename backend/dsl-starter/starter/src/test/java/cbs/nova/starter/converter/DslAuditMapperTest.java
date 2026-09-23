package cbs.nova.starter.converter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.entity.DslAuditEntity;
import cbs.nova.starter.model.DslAudit;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class DslAuditMapperTest {

  private final DslAuditMapper mapper = Mappers.getMapper(DslAuditMapper.class);

  private static final Instant OCCURRED_AT = Instant.parse("2026-09-01T12:00:00Z");

  @Test
  void mapsDomainToEntityFieldForField() {
    DslAudit audit = new DslAudit(
            null, OCCURRED_AT, "admin", "PUBLISH", "OrderFlow", "corr-1", "SUCCESS",
            "{\"detail\":1}");

    DslAuditEntity entity = mapper.toEntity(audit);

    assertThat(entity.id()).isNull();
    assertThat(entity.occurredAt()).isEqualTo(OCCURRED_AT);
    assertThat(entity.actor()).isEqualTo("admin");
    assertThat(entity.action()).isEqualTo("PUBLISH");
    assertThat(entity.target()).isEqualTo("OrderFlow");
    assertThat(entity.correlationId()).isEqualTo("corr-1");
    assertThat(entity.outcome()).isEqualTo("SUCCESS");
    assertThat(entity.detailsJson()).isEqualTo("{\"detail\":1}");
  }

  @Test
  void mapsEntityToDomainFieldForField() {
    DslAuditEntity entity = new DslAuditEntity(
            9L, OCCURRED_AT, "system", "EXECUTE", "Ping", null, "FAILED", null);

    DslAudit audit = mapper.toDomain(entity);

    assertThat(audit.id()).isEqualTo(9L);
    assertThat(audit.occurredAt()).isEqualTo(OCCURRED_AT);
    assertThat(audit.actor()).isEqualTo("system");
    assertThat(audit.action()).isEqualTo("EXECUTE");
    assertThat(audit.target()).isEqualTo("Ping");
    assertThat(audit.correlationId()).isNull();
    assertThat(audit.outcome()).isEqualTo("FAILED");
    assertThat(audit.detailsJson()).isNull();
  }

  @Test
  void roundTripPreservesAllFields() {
    DslAudit original = new DslAudit(
            5L, OCCURRED_AT, "admin", "DELETE", "OldFlow", "corr-9", "SUCCESS", "{}");

    DslAudit roundTrip = mapper.toDomain(mapper.toEntity(original));

    assertThat(roundTrip).isEqualTo(original);
  }

  @Test
  void nullableFieldsMapAsNulls() {
    DslAudit audit = new DslAudit(
            null, OCCURRED_AT, "admin", "LOGIN", "admin-ui", null, "SUCCESS", null);

    DslAuditEntity entity = mapper.toEntity(audit);

    assertThat(entity.id()).isNull();
    assertThat(entity.correlationId()).isNull();
    assertThat(entity.detailsJson()).isNull();
  }
}
