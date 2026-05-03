package cbs.dsl.api;

import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Consolidated mass operation DSL types.
 *
 * <p>MassOperationInput carries the raw parameters and business date for executing a mass
 * operation. MassOperationOutput wraps the batch processing results and status.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MassOperationTypes {

  /**
   * Input carrier for mass operation execution. Carries parameters and business date as
   * JSON-serializable data for PostgreSQL JSONB storage.
   */
  @Json
  @Builder(toBuilder = true)
  public record MassOperationInput(Map<String, Object> params, String businessDate) {

    /**
     * Creates a new MassOperationInput with the given parameters and null businessDate.
     *
     * @param params the parameters map
     */
    public MassOperationInput(Map<String, Object> params) {
      this(params, null);
    }

    /**
     * Creates a new MassOperationInput with the given parameters and business date.
     *
     * @param params the parameters map
     * @param businessDate the business date string
     * @return a new MassOperationInput instance
     */
    public static MassOperationInput of(Map<String, Object> params, String businessDate) {
      return new MassOperationInput(params, businessDate);
    }

    /**
     * Returns parameters with nulls filtered out, suitable for MassOperationContext.
     *
     * @return map with null values excluded
     */
    public Map<String, Object> nonNullParams() {
      return params.entrySet().stream()
          .filter(e -> e.getValue() != null)
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
  }

  /**
   * Output carrier for mass operation execution. Carries batch processing results and status as
   * JSON-serializable data for PostgreSQL JSONB storage.
   */
  @Json
  @Builder(toBuilder = true)
  public record MassOperationOutput(int processedCount, int failedCount, String status) {

    /**
     * Creates a new MassOperationOutput with the given counts and status = "COMPLETED".
     *
     * @param processedCount the number of successfully processed items
     * @param failedCount the number of failed items
     */
    public MassOperationOutput(int processedCount, int failedCount) {
      this(processedCount, failedCount, "COMPLETED");
    }

    /**
     * Creates a new MassOperationOutput with COMPLETED status.
     *
     * @param processedCount the number of successfully processed items
     * @param failedCount the number of failed items
     * @return a new MassOperationOutput instance with COMPLETED status
     */
    public static MassOperationOutput completed(int processedCount, int failedCount) {
      return new MassOperationOutput(processedCount, failedCount, "COMPLETED");
    }

    /**
     * Creates a new MassOperationOutput with PARTIAL status.
     *
     * @param processedCount the number of successfully processed items
     * @param failedCount the number of failed items
     * @return a new MassOperationOutput instance with PARTIAL status
     */
    public static MassOperationOutput partial(int processedCount, int failedCount) {
      return new MassOperationOutput(processedCount, failedCount, "PARTIAL");
    }
  }
}
