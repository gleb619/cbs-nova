package cbs.app.temporal.massop;

import cbs.dsl.api.MassOperationDefinition;
import cbs.dsl.api.context.MassOperationContext;
import cbs.dsl.runtime.DslRegistry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class MassOpItemActivityImpl implements MassOpItemActivity {

  private static final Logger LOG = LoggerFactory.getLogger(MassOpItemActivityImpl.class);

  private final DslRegistry dslRegistry;
  private final ObjectMapper objectMapper;

  public MassOpItemActivityImpl(DslRegistry dslRegistry, ObjectMapper objectMapper) {
    this.dslRegistry = dslRegistry;
    this.objectMapper = objectMapper;
  }

  @Override
  public MassOpItemResult processItem(MassOpItemInput input) {
    // 5a. Look up MassOperationDefinition
    MassOperationDefinition massOpDef = dslRegistry.getMassOperations().get(input.massOpCode());
    if (massOpDef == null) {
      return new MassOpItemResult(false, "MassOp not found: " + input.massOpCode());
    }

    // 5b. Build context
    @SuppressWarnings("unchecked")
    Map<String, Object> itemData;
    try {
      itemData = objectMapper.readValue(input.itemDataJson(), Map.class);
    } catch (JsonProcessingException e) {
      return new MassOpItemResult(false, "Invalid item data JSON: " + e.getMessage());
    }

    MassOperationContext ctx = new MassOperationContext(
        input.itemId(),
        itemData,
        input.massOperationExecutionId(),
        input.performedBy(),
        input.dslVersion());

    // 5c. Call itemBlock
    try {
      massOpDef.getItemBlock().invoke(ctx);
      // 5e. On success
      return new MassOpItemResult(true, null);
    } catch (Exception e) {
      // 5d. On exception
      return new MassOpItemResult(false, e.getMessage());
    }
  }
}
