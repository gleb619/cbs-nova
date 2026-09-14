package cbs.nova.dsl.codegen.task;

import cbs.nova.dsl.codegen.SemanticValidator;
import cbs.nova.dsl.registry.HelperRegistry;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class ValidateDescriptorsTask implements CompileTask {

  private final SemanticValidator semanticValidator;
  private final HelperRegistry helperRegistry;

  @Override
  public String name() {
    return "validate";
  }

  @Override
  public CompileContext run(CompileContext context) {
    semanticValidator.validate(
            context.processes(), context.transactions(), context.functions(), helperRegistry);

    return context;
  }
}
