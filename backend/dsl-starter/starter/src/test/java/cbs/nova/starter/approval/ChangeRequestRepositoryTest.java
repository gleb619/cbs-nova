package cbs.nova.starter.approval;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.starter.NotificationTestApplication;
import cbs.nova.starter.entity.ChangeRequestEntity;
import cbs.nova.starter.entity.ChangeRequestEntity.Status;
import cbs.nova.starter.persistence.ChangeRequestRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

/**
 * H2 round-trip tests for {@link ChangeRequestRepository} (T568): generated keys, pending lookup,
 * filtered listings newest first, and status transitions.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE, classes = NotificationTestApplication.class)
@Sql(scripts = {"classpath:db/migration/h2/V1__init.sql",
    "classpath:sql/truncate-change-requests.sql"})
@TestPropertySource(properties = {
    "cbs.dsl.worker.enabled=false"
})
class ChangeRequestRepositoryTest {

  @Autowired
  private ChangeRequestRepository repository;

  @Test
  void insertReturnsGeneratedIdAndFindByIdRoundTrips() {
    long id = repository.insert(request("LoanA", "alice", Status.PENDING));

    Optional<ChangeRequestEntity> found = repository.findById(id);

    assertThat(found).isPresent();
    assertThat(found.get().id()).isEqualTo(id);
    assertThat(found.get().definitionName()).isEqualTo("LoanA");
    assertThat(found.get().requestedBy()).isEqualTo("alice");
    assertThat(found.get().status()).isEqualTo(Status.PENDING);
    assertThat(found.get().draftContent()).contains("\"name\":\"LoanA\"");
    assertThat(found.get().requestedAt()).isNotNull();
    assertThat(found.get().approvedBy()).isNull();
    assertThat(found.get().approvedAt()).isNull();
    assertThat(found.get().comment()).isNull();
    assertThat(repository.count()).isEqualTo(1);
  }

  @Test
  void findPendingByDefinitionNameReturnsOnlyPendingRow() {
    long first = repository.insert(request("LoanA", "alice", Status.PENDING));
    repository.updateStatus(first, Status.SUPERSEDED, null, null, null);
    long second = repository.insert(request("LoanA", "bob", Status.PENDING));
    repository.insert(request("LoanB", "alice", Status.PENDING));

    Optional<ChangeRequestEntity> pending = repository.findPendingByDefinitionName("LoanA");

    assertThat(pending).isPresent();
    assertThat(pending.get().id()).isEqualTo(second);
    assertThat(pending.get().requestedBy()).isEqualTo("bob");
  }

  @Test
  void findAllFiltersByDefinitionNameAndStatusNewestFirst() {
    long a1 = repository.insert(request("LoanA", "alice", Status.PENDING));
    long a2 = repository.insert(request("LoanA", "bob", Status.PENDING));
    repository.updateStatus(a1, Status.APPROVED, "carol", Instant.now(), "ok");
    long b = repository.insert(request("LoanB", "alice", Status.PENDING));

    List<ChangeRequestEntity> all = repository.findAll(null, null);
    assertThat(all).extracting(ChangeRequestEntity::id).containsExactly(b, a2, a1);

    List<ChangeRequestEntity> byName = repository.findAll("LoanA", null);
    assertThat(byName).extracting(ChangeRequestEntity::id).containsExactly(a2, a1);

    List<ChangeRequestEntity> byNameAndStatus = repository.findAll("LoanA", Status.APPROVED);
    assertThat(byNameAndStatus).extracting(ChangeRequestEntity::id).containsExactly(a1);

    List<ChangeRequestEntity> byStatus = repository.findAll(null, Status.PENDING);
    assertThat(byStatus).extracting(ChangeRequestEntity::definitionName)
            .containsExactly("LoanB", "LoanA");

    assertThat(repository.findByDefinitionName("LoanB")).extracting(ChangeRequestEntity::id)
            .containsExactly(b);
  }

  @Test
  void updateStatusApproveAndRejectRoundTrip() {
    long id = repository.insert(request("LoanA", "alice", Status.PENDING));
    Instant decidedAt = Instant.now();

    repository.updateStatus(id, Status.APPROVED, "carol", decidedAt, "looks good");

    ChangeRequestEntity approved = repository.findById(id).orElseThrow();
    assertThat(approved.status()).isEqualTo(Status.APPROVED);
    assertThat(approved.approvedBy()).isEqualTo("carol");
    // H2 stores TIMESTAMP WITH TIME ZONE at microsecond precision (rounding), not nanoseconds.
    assertThat(approved.approvedAt()).isBetween(decidedAt.minusMillis(1), decidedAt.plusMillis(1));
    assertThat(approved.comment()).isEqualTo("looks good");

    repository.updateStatus(id, Status.REJECTED, null, null, "no");

    ChangeRequestEntity rejected = repository.findById(id).orElseThrow();
    assertThat(rejected.status()).isEqualTo(Status.REJECTED);
    assertThat(rejected.approvedBy()).isNull();
    assertThat(rejected.approvedAt()).isNull();
    assertThat(rejected.comment()).isEqualTo("no");
  }

  private static ChangeRequestEntity request(String definitionName, String requestedBy,
          Status status) {
    return new ChangeRequestEntity(null, definitionName,
            "{\"name\":\"" + definitionName + "\",\"type\":\"process\",\"status\":\"Draft\","
                    + "\"version\":\"1\"}",
            requestedBy, Instant.now(), status, null, null, null);
  }
}
