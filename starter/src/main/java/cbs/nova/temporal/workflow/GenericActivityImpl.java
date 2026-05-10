package cbs.nova.temporal.workflow;

import cbs.dsl.api.HelperDefinition;
import cbs.dsl.api.HelperTypes.HelperInput;
import cbs.dsl.api.HelperTypes.HelperOutput;
import cbs.nova.registry.DslRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class GenericActivityImpl implements GenericActivity {

  private final DslRegistry dslRegistry;

  @Override
  public HelperOutput prepare(String helperCode, Map<String, Object> params) {
    log.debug("Preparing helper: code={}", helperCode);
    HelperDefinition helper = dslRegistry.resolveHelper(helperCode);
    return new HelperOutput(params);
  }

  @Override
  public HelperOutput execute(String helperCode, HelperInput input) {
    log.debug("Executing helper: code={}", helperCode);
    HelperDefinition helper = dslRegistry.resolveHelper(helperCode);
    return helper.execute(input);
  }
}
