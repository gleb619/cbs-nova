package cbs.dsl.api.context;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

//TODO: replace with real impl context in correspondent classes
@Deprecated(forRemoval = true)
public record EnrichmentContext(
    String eventCode,
    Long workflowExecutionId,
    String performedBy,
    String dslVersion,
    Map<String, Object> eventParameters,
    Map<String, Object> enrichment,
    BiFunction<String, Map<String, Object>, Object> helperResolver) {

  public static Builder builder() {
    return new Builder();
  }

  public Object get(String key) {
    return eventParameters != null ? eventParameters.get(key) : null;
  }

  public Object getOrDefault(String key, Object defaultValue) {
    return eventParameters != null ? eventParameters.getOrDefault(key, defaultValue) : defaultValue;
  }

  public void set(String key, Object value) {
    enrichment().put(key, value);
  }

  public void put(String key, Object value) {
    set(key, value);
  }

  public Object helper(String name, Map<String, Object> params) {
    if (helperResolver != null) {
      return helperResolver.apply(name, params);
    }
    return null;
  }

  public static final class Builder {
    private String eventCode;
    private Long workflowExecutionId;
    private String performedBy;
    private String dslVersion;
    private Map<String, Object> eventParameters;
    private Map<String, Object> enrichment = new HashMap<>();
    private BiFunction<String, Map<String, Object>, Object> helperResolver;

    public Builder eventCode(String eventCode) {
      this.eventCode = eventCode;
      return this;
    }

    public Builder workflowExecutionId(Long workflowExecutionId) {
      this.workflowExecutionId = workflowExecutionId;
      return this;
    }

    public Builder performedBy(String performedBy) {
      this.performedBy = performedBy;
      return this;
    }

    public Builder dslVersion(String dslVersion) {
      this.dslVersion = dslVersion;
      return this;
    }

    public Builder eventParameters(Map<String, Object> eventParameters) {
      this.eventParameters = eventParameters;
      return this;
    }

    public Builder enrichment(Map<String, Object> enrichment) {
      this.enrichment = enrichment;
      return this;
    }

    public Builder helperResolver(BiFunction<String, Map<String, Object>, Object> helperResolver) {
      this.helperResolver = helperResolver;
      return this;
    }

    public EnrichmentContext build() {
      return new EnrichmentContext(
          eventCode,
          workflowExecutionId,
          performedBy,
          dslVersion,
          eventParameters,
          enrichment,
          helperResolver);
    }
  }
}