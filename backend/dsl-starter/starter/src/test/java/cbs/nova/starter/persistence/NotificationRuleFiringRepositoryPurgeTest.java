package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class NotificationRuleFiringRepositoryPurgeTest {

  private final NotificationRuleFiringCrudRepository crud = mock(
          NotificationRuleFiringCrudRepository.class);
  private final NotificationRuleFiringRepository repository = new NotificationRuleFiringRepository(
          crud, null);

  @Test
  void purgeOlderThanDelegatesToCrud() {
    Instant cutoff = Instant.parse("2025-01-01T00:00:00Z");
    when(crud.deleteOlderThan(cutoff, 500)).thenReturn(13);

    int purged = repository.purgeOlderThan(cutoff, 500);

    assertThat(purged).isEqualTo(13);
  }

  @Test
  void purgeOlderThanRejectsNullCutoff() {
    assertThatThrownBy(() -> repository.purgeOlderThan(null, 100))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("cutoff");
    verifyCrudUntouched();
  }

  @Test
  void purgeOlderThanRejectsNonPositiveBatchSize() {
    Instant cutoff = Instant.parse("2025-01-01T00:00:00Z");
    assertThatThrownBy(() -> repository.purgeOlderThan(cutoff, 0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batchSize");
    assertThatThrownBy(() -> repository.purgeOlderThan(cutoff, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("batchSize");
    verifyCrudUntouched();
  }

  private void verifyCrudUntouched() {
    org.mockito.Mockito.verify(crud, never()).deleteOlderThan(any(Instant.class), anyInt());
  }
}
