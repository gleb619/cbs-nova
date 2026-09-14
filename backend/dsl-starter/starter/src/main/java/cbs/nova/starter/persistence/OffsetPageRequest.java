package cbs.nova.starter.persistence;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;

/**
 * A {@link Pageable} keyed by an arbitrary row offset rather than a page number, so callers can
 * pass any {@code offset}/{@code limit} pair (not just multiples of the page size) — matching the
 * hand-rolled JDBC repository this replaces.
 */
final class OffsetPageRequest implements Pageable {

  private final long offset;
  private final int limit;
  private final Sort sort;

  OffsetPageRequest(long offset, int limit, Sort sort) {
    this.offset = offset;
    this.limit = limit;
    this.sort = sort;
  }

  @Override
  public int getPageNumber() {
    return (int) (offset / limit);
  }

  @Override
  public int getPageSize() {
    return limit;
  }

  @Override
  public long getOffset() {
    return offset;
  }

  @Override
  @NonNull
  public Sort getSort() {
    return sort;
  }

  @Override
  @NonNull
  public Pageable next() {
    return new OffsetPageRequest(offset + limit, limit, sort);
  }

  @Override
  @NonNull
  public Pageable previousOrFirst() {
    return hasPrevious() ? new OffsetPageRequest(offset - limit, limit, sort) : first();
  }

  @Override
  @NonNull
  public Pageable first() {
    return new OffsetPageRequest(0, limit, sort);
  }

  @Override
  @NonNull
  public Pageable withPage(int pageNumber) {
    return new OffsetPageRequest((long) pageNumber * limit, limit, sort);
  }

  @Override
  public boolean hasPrevious() {
    return offset > 0;
  }
}
