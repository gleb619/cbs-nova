package cbs.nova.starter.model;

/**
 * Query-parameter records extracted from {@code ServerRequest} by
 * {@link cbs.nova.starter.converter.RequestQueryConverter}. Keeping the records in the model
 * package (alongside {@link DslIntrospectionModels}) keeps the converter pure and lets handlers
 * consume typed values without touching the raw servlet request API.
 */
public final class RequestQueryModels {

  private RequestQueryModels() {
  }

  /**
   * Filters for {@code GET /api/executions} and {@code GET /api/executions/export.csv}.
   *
   * <p>
   * The four filters are populated independently: {@code processName} blanks out, {@code status}
   * and {@code mode} pass through (including empty strings), and {@code correlationId} is trimmed
   * and blanked out. The repository contract treats {@code null} as "no filter" and empty strings
   * as "match exactly that empty string" — the asymmetry is preserved here so the wire behavior is
   * unchanged by the extraction.
   */
  public record ExecutionListQuery(
          String processName,
          String status,
          String mode,
          String correlationId) {
  }

  /**
   * Filters for {@code GET /api/dsl/objects/search}. All three params are simple passthroughs;
   * empty values are passed through to the search service as-is.
   */
  public record IntrospectionSearchQuery(
          String name,
          String type,
          String description) {
  }
}
