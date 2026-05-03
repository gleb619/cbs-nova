package cbs.nova.sample;

import cbs.dsl.api.Action;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.TransitionRuleDefinition;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.api.WorkflowTypes.WorkflowInput;
import cbs.dsl.api.WorkflowTypes.WorkflowOutput;
import cbs.dsl.api.context.FinishContext;
import cbs.dsl.builder.EventDsl;
import cbs.nova.registry.DslRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Registers the showcase DSL definitions: {@code SHOWCASE_EVENT} event and {@code SHOWCASE_WF}
 * workflow.
 *
 * <p>The showcase demonstrates the full chain: event → context (calls helper) → transactions (calls
 * transaction) → finish (logs).
 */
@Component
@Slf4j
@Deprecated
@RequiredArgsConstructor
public class ShowcaseConfig implements ApplicationRunner {

  private final DslRegistry registry;

  @Override
  public void run(ApplicationArguments args) {
    registerShowcaseEvent();
    registerShowcaseWorkflow();
    log.info("Showcase DSL definitions registered: SHOWCASE_EVENT, SHOWCASE_WF");
  }

  private void registerShowcaseEvent() {
    EventDefinition event = EventDsl.event("SHOWCASE_EVENT")
        .requiredParam("name")
        .requiredParam("value")
        .context(ctx -> {
          // Call SAMPLE_HELPER via helper resolution
          var helperResult = ctx.helper("SAMPLE_HELPER", Map.of("someVal", ctx.get("name")));
          ctx.put("enriched_greeting", helperResult);

          // Evaluate SAMPLE_CONDITION
          var conditionResult = ctx.helper("SAMPLE_CONDITION", Map.of("name", ctx.get("name")));
          ctx.put("conditionPassed", conditionResult);
          log.info(
              "[SHOWCASE_EVENT] context: helper={}, condition={}", helperResult, conditionResult);
        })
        .transaction("SAMPLE_TX")
        .finish((FinishContext ctx, Throwable ex) -> {
          if (ex != null) {
            log.error("[SHOWCASE_EVENT] finish: execution failed", ex);
          } else {
            log.info("[SHOWCASE_EVENT] finish: completed. enrichment={}", ctx.getEnrichment());
          }
        })
        .build();

    registry.register(event);
  }

  //TODO: redo, add a Workflow dsl instead
  private void registerShowcaseWorkflow() {
    EventDefinition showcaseEvent = registry.resolveEvent("SHOWCASE_EVENT");

    WorkflowDefinition workflow = new WorkflowDefinition() {
      @Override
      public String getCode() {
        return "SHOWCASE_WF";
      }

      @Override
      public List<String> getStates() {
        return List.of("START", "DONE", "FAULTED");
      }

      @Override
      public String getInitial() {
        return "START";
      }

      @Override
      public List<String> getTerminalStates() {
        return List.of("DONE");
      }

      @Override
      public List<TransitionRuleDefinition> getTransitions() {
        return List.of(
            new TransitionRuleDefinition("START", "DONE", Action.SUBMIT, showcaseEvent, "FAULTED"));
      }

      @Override
      public WorkflowOutput execute(WorkflowInput input) {
        return new WorkflowOutput("DONE", List.of(), "ACTIVE");
      }
    };

    registry.register(workflow);
  }
}
