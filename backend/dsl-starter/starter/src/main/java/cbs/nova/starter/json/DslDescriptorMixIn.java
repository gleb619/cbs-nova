package cbs.nova.starter.json;

import cbs.nova.dsl.DslDescriptor;
import cbs.nova.dsl.DslObject.DslType;
import cbs.nova.dsl.ParameterDescriptor;
import java.time.Duration;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.json.JsonMapper;

//TODO: It is not acceptable to create crutches. to remove, instead we need to add some method to a misc-codegen, for
// helper descriptor
@Deprecated(forRemoval = true)
@JsonIgnoreProperties("objectDescriptor")
public abstract class DslDescriptorMixIn {

  @JsonProperty
  public abstract String name();
  @JsonProperty
  public abstract DslType type();
  @JsonProperty
  public abstract String description();
  @JsonProperty
  public abstract Class<?> inputType();
  @JsonProperty
  public abstract Class<?> outputType();
  @JsonProperty
  public abstract boolean hasSideEffects();
  @JsonProperty
  public abstract List<ParameterDescriptor> parameters();
  @JsonProperty
  public abstract String taskQueue();
  @JsonProperty
  public abstract String version();
  @JsonProperty
  public abstract Duration startToCloseTimeout();
  @JsonProperty
  public abstract Duration heartbeatTimeout();

  public static JsonMapper mapper() {
    return JsonMapper.builder()
            .addMixIn(DslDescriptor.class, DslDescriptorMixIn.class)
            .build();
  }
}
