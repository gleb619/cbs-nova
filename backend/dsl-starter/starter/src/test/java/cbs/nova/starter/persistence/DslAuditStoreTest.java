package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.starter.converter.DslAuditMapper;
import cbs.nova.starter.entity.DslAuditEntity;
import cbs.nova.starter.model.DslAudit;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;

class DslAuditStoreTest {

  private final DslAuditCrudRepository repository = mock(DslAuditCrudRepository.class);
  private final DslAuditMapper mapper = mock(DslAuditMapper.class);
  private final DslAuditStore store = new DslAuditStore(repository, mapper);

  @Test
  void searchRejectsNegativeOffset() {
    assertThatThrownBy(() -> store.search(null, -1, 10))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("offset");
  }

  @Test
  void searchRejectsNonPositiveLimit() {
    assertThatThrownBy(() -> store.search(null, 0, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("limit");
  }

  @Test
  void searchWithoutActionUsesUnfilteredFinder() {
    Page<DslAuditEntity> page = pageWith(entity(1L), 1L, domain(1L));
    when(repository.findAllByOrderByOccurredAtDescIdDesc(any())).thenReturn(page);

    DslAuditSearchResult result = store.search(null, 0, 10);

    verify(repository).findAllByOrderByOccurredAtDescIdDesc(any());
    verify(repository, never()).findByActionOrderByOccurredAtDescIdDesc(any(), any());
    assertThat(result.items()).containsExactly(domain(1L));
    assertThat(result.total()).isEqualTo(1L);
  }

  @Test
  void searchWithBlankActionUsesUnfilteredFinder() {
    Page<DslAuditEntity> page = pageWith(entity(2L), 1L, domain(2L));
    when(repository.findAllByOrderByOccurredAtDescIdDesc(any())).thenReturn(page);

    DslAuditSearchResult result = store.search("  ", 5, 10);

    verify(repository).findAllByOrderByOccurredAtDescIdDesc(any());
    verify(repository, never()).findByActionOrderByOccurredAtDescIdDesc(any(), any());
    assertThat(result.items()).containsExactly(domain(2L));
    assertThat(result.total()).isEqualTo(1L);
  }

  @Test
  void searchWithActionUsesFilteredFinder() {
    Page<DslAuditEntity> page = pageWith(entity(3L), 1L, domain(3L));
    when(repository.findByActionOrderByOccurredAtDescIdDesc(any(), any())).thenReturn(page);

    DslAuditSearchResult result = store.search("DEFINITION_PUBLISH", 0, 10);

    verify(repository).findByActionOrderByOccurredAtDescIdDesc(eq("DEFINITION_PUBLISH"), any());
    verify(repository, never()).findAllByOrderByOccurredAtDescIdDesc(any());
    assertThat(result.items()).containsExactly(domain(3L));
    assertThat(result.total()).isEqualTo(1L);
  }

  @Test
  void appendDelegatesToMapperAndRepository() {
    DslAudit row = domain(4L);
    DslAuditEntity entity = entity(4L);
    when(mapper.toEntity(row)).thenReturn(entity);

    store.append(row);

    verify(mapper).toEntity(row);
    verify(repository).save(entity);
  }

  private Page<DslAuditEntity> pageWith(DslAuditEntity entity, long total, DslAudit domain) {
    when(mapper.toDomain(entity)).thenReturn(domain);
    Page<DslAuditEntity> page = mock(Page.class);
    when(page.getContent()).thenReturn(List.of(entity));
    when(page.getTotalElements()).thenReturn(total);
    return page;
  }

  private DslAuditEntity entity(long id) {
    return new DslAuditEntity(id, Instant.parse("2026-09-17T10:00:00Z"),
            "operator-1", "DEFINITION_PUBLISH", "LoanFlow", null, "SUCCESS", null);
  }

  private DslAudit domain(long id) {
    return new DslAudit(id, Instant.parse("2026-09-17T10:00:00Z"),
            "operator-1", "DEFINITION_PUBLISH", "LoanFlow", null, "SUCCESS", null);
  }
}
