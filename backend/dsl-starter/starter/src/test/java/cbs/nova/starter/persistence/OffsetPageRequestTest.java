package cbs.nova.starter.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Unit spec for {@link OffsetPageRequest} (T534).
 *
 * <p>
 * Note: the mid-range {@code previousOrFirst()} case below documents the current production
 * behavior. If the implementation is later fixed to clamp to {@link #first()} when the computed
 * offset would become negative, this assertion must be updated accordingly.
 */
class OffsetPageRequestTest {

  private static final Sort SORT = Sort.by("id").ascending();

  @Test
  void constructionExposesOffsetPageSizeAndSort() {
    OffsetPageRequest request = new OffsetPageRequest(25L, 10, SORT);

    assertThat(request.getOffset()).isEqualTo(25L);
    assertThat(request.getPageSize()).isEqualTo(10);
    assertThat(request.getSort()).isEqualTo(SORT);
  }

  @Test
  void getPageNumberUsesIntegerTruncation() {
    assertThat(new OffsetPageRequest(20L, 10, SORT).getPageNumber()).isEqualTo(2);
    assertThat(new OffsetPageRequest(25L, 10, SORT).getPageNumber()).isEqualTo(2);
  }

  @Test
  void nextAdvancesOffsetByLimitAndKeepsSortAndLimit() {
    OffsetPageRequest current = new OffsetPageRequest(20L, 10, SORT);
    Pageable next = current.next();

    assertThat(next.getOffset()).isEqualTo(30L);
    assertThat(next.getPageSize()).isEqualTo(10);
    assertThat(next.getSort()).isEqualTo(SORT);
  }

  @Test
  void firstResetsOffsetToZeroAndKeepsSortAndLimit() {
    OffsetPageRequest current = new OffsetPageRequest(55L, 10, SORT);
    Pageable first = current.first();

    assertThat(first.getOffset()).isEqualTo(0L);
    assertThat(first.getPageSize()).isEqualTo(10);
    assertThat(first.getSort()).isEqualTo(SORT);
  }

  @Test
  void withPageComputesOffsetFromPageNumberAndLimit() {
    Pageable page = new OffsetPageRequest(0L, 10, SORT).withPage(3);

    assertThat(page.getOffset()).isEqualTo(30L);
    assertThat(page.getPageSize()).isEqualTo(10);
    assertThat(page.getSort()).isEqualTo(SORT);
  }

  @Test
  void hasPreviousIsFalseAtZeroAndTrueOtherwise() {
    assertThat(new OffsetPageRequest(0L, 10, SORT).hasPrevious()).isFalse();
    assertThat(new OffsetPageRequest(1L, 10, SORT).hasPrevious()).isTrue();
    assertThat(new OffsetPageRequest(10L, 10, SORT).hasPrevious()).isTrue();
  }

  @Test
  void previousOrFirstStepsBackCleanlyWhenOffsetCoversAtLeastOnePage() {
    OffsetPageRequest current = new OffsetPageRequest(20L, 10, SORT);
    Pageable previous = current.previousOrFirst();

    assertThat(previous.getOffset()).isEqualTo(10L);
    assertThat(previous.getPageSize()).isEqualTo(10);
    assertThat(previous.getSort()).isEqualTo(SORT);
  }

  @Test
  void previousOrFirstProducesNegativeOffsetForMidRangeOffset() {
    // offset=5, limit=10: hasPrevious() is true because offset > 0, so the implementation
    // subtracts the limit. This documents the current (arguably buggy) behavior — see T534.
    OffsetPageRequest current = new OffsetPageRequest(5L, 10, SORT);
    Pageable previous = current.previousOrFirst();

    assertThat(previous.getOffset()).isEqualTo(-5L);
    assertThat(previous.getPageSize()).isEqualTo(10);
    assertThat(previous.getSort()).isEqualTo(SORT);
  }

  @Test
  void previousOrFirstAtZeroReturnsFirst() {
    OffsetPageRequest current = new OffsetPageRequest(0L, 10, SORT);
    Pageable previous = current.previousOrFirst();

    assertThat(previous.getOffset()).isEqualTo(0L);
    assertThat(previous.getPageSize()).isEqualTo(10);
    assertThat(previous.getSort()).isEqualTo(SORT);
  }
}
