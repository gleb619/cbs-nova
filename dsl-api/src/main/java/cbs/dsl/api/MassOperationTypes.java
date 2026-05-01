package cbs.dsl.api;

import cbs.dsl.api.MassOperationFunction.MassOperationArg;
import cbs.dsl.api.MassOperationFunction.MassOperationResult;
import io.avaje.jsonb.Json;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

import java.util.Map;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class MassOperationTypes {

  @Json
  @Builder(toBuilder = true)
  public record MassOperationInput(Map<String, Object> params, String massOperationCode)
      implements MassOperationArg {

    @Override
    public Map<String, Object> toMap() {
      return params;
    }
  }

  @Json
  @Builder(toBuilder = true)
  public record MassOperationOutput(long processedCount, long failedCount, String status)
      implements MassOperationResult {

    @Override
    public Map<String, Object> toMap() {
      return Map.of(
          "processedCount", processedCount,
          "failedCount", failedCount,
          "status", status);
    }
  }
}
