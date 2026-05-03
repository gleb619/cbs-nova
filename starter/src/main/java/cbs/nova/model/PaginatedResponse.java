package cbs.nova.model;

import java.util.List;
import lombok.Builder;

/** Paginated result returned by service layer, decoupled from Spring Data {@code Page}. */
@Builder(toBuilder = true)
public record PaginatedResponse<T>(
    List<T> content, long totalElements, int pageNumber, int pageSize, int totalPages) {

  public static <T> PaginatedResponse<T> of(
      List<T> content, long totalElements, int pageNumber, int pageSize) {
    int totalPages = pageSize > 0 ? (int) Math.ceil((double) totalElements / pageSize) : 0;
    return new PaginatedResponse<>(content, totalElements, pageNumber, pageSize, totalPages);
  }
}
