package cbs.nova.starter.json;

import cbs.nova.dsl.DslDescriptor;
import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

/**
 * Jackson mapper for hashing {@link DslDescriptor}s (preview cache keys, run definition hash,
 * schema cache keys).
 *
 * <p>
 * {@link DslDescriptor} exposes fluent accessors ({@code name()} instead of {@code getName()}) plus
 * a raw {@code objectDescriptor} getter, so a plain {@code JsonMapper} would serialize the wrong
 * shape. An explicit {@link ValueSerializer} pins the exact property set and order — alphabetical:
 * description, heartbeatTimeout, inputType, name, outputType, parameters,
 * startToCloseTimeout, taskQueue, type, version — matching the serialized form previously produced
 * via the {@code DslDescriptorMixIn}.
 */
public final class DslDescriptorMapper {

  private DslDescriptorMapper() {
  }

  public static JsonMapper mapper() {
    SimpleModule module = new SimpleModule();
    module.addSerializer(DslDescriptor.class, new DslDescriptorSerializer());
    return JsonMapper.builder()
            .addModule(module)
            .build();
  }

  static final class DslDescriptorSerializer extends ValueSerializer<DslDescriptor> {

    @Override
    public void serialize(DslDescriptor value, JsonGenerator gen, SerializationContext ctxt)
            throws JacksonException {
      gen.writeStartObject();
      ctxt.defaultSerializeProperty("description", value.description(), gen);
      ctxt.defaultSerializeProperty("heartbeatTimeout", value.heartbeatTimeout(), gen);
      ctxt.defaultSerializeProperty("inputType", value.inputType(), gen);
      ctxt.defaultSerializeProperty("name", value.name(), gen);
      ctxt.defaultSerializeProperty("outputType", value.outputType(), gen);
      ctxt.defaultSerializeProperty("parameters", value.parameters(), gen);
      ctxt.defaultSerializeProperty("startToCloseTimeout", value.startToCloseTimeout(), gen);
      ctxt.defaultSerializeProperty("taskQueue", value.taskQueue(), gen);
      ctxt.defaultSerializeProperty("type", value.type(), gen);
      ctxt.defaultSerializeProperty("version", value.version(), gen);
      gen.writeEndObject();
    }
  }
}
