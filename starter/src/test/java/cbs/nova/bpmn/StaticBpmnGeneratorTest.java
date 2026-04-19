package cbs.nova.bpmn;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.dsl.api.Action;
import cbs.dsl.api.EventDefinition;
import cbs.dsl.api.TransactionDefinition;
import cbs.dsl.api.TransitionRule;
import cbs.dsl.api.WorkflowDefinition;
import cbs.dsl.api.context.EnrichmentContext;
import cbs.dsl.api.context.FinishContext;
import cbs.dsl.api.context.TransactionsScope;
import java.util.List;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function1;
import kotlin.jvm.functions.Function2;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StaticBpmnGeneratorTest {

  private static final Function1<EnrichmentContext, Unit> NOOP_ENRICH = ctx -> Unit.INSTANCE;
  private static final Function1<FinishContext, Unit> NOOP_FINISH = ctx -> Unit.INSTANCE;
  private static final List<TransactionDefinition> NO_TX = List.of();

  private static EventDefinition simpleEvent(String code) {
    return new EventDefinition() {
      @Override
      public String getCode() {
        return code;
      }

      @Override
      public Function1<EnrichmentContext, Unit> getContextBlock() {
        return NOOP_ENRICH;
      }

      @Override
      public Function1<FinishContext, Unit> getDisplayBlock() {
        return NOOP_FINISH;
      }

      @Override
      public @Nullable Function2<@NotNull TransactionsScope, @NotNull Continuation<? super Unit>, Object> getTransactionsBlock() {
        return (_, _) -> null;
      }

      @Override
      public Function1<FinishContext, Unit> getFinishBlock() {
        return NOOP_FINISH;
      }
    };
  }

  @Test
  @DisplayName("shouldGenerateValidBpmnXmlForMinimalWorkflow")
  void shouldGenerateValidBpmnXmlForMinimalWorkflow() {
    // Given: a minimal 2-state workflow
    EventDefinition approveEvent = simpleEvent("approve");

    WorkflowDefinition workflow = new WorkflowDefinition() {

      @Override
      public String getCode() {
        return "loan-approval";
      }

      @Override
      public List<String> getStates() {
        return List.of("PENDING", "DONE");
      }

      @Override
      public String getInitial() {
        return "PENDING";
      }

      @Override
      public List<String> getTerminalStates() {
        return List.of("DONE");
      }

      @Override
      public List<TransitionRule> getTransitions() {
        return List.of(
            new TransitionRule("PENDING", "DONE", Action.APPROVE, approveEvent, "FAULTED", null));
      }

    };

    // When
    StaticBpmnGenerator generator = new StaticBpmnGenerator();
    String xml = generator.generate(workflow);

    // Then
    assertThat(xml).contains("xmlns:bpmn=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"");
    assertThat(xml).contains("<bpmn:process id=\"loan-approval\"");
    assertThat(xml).contains("<bpmn:startEvent id=\"start\"");
    assertThat(xml).contains("<bpmn:userTask id=\"PENDING\"");
    assertThat(xml).contains("<bpmn:endEvent id=\"DONE\"");
    assertThat(xml).contains("sourceRef=\"PENDING\"");
    assertThat(xml).contains("targetRef=\"DONE\"");
    assertThat(xml).contains("name=\"approve\"");
  }

  @Test
  @DisplayName("shouldRenderAllStatesAsUserTaskWhenNoTerminalStates")
  void shouldRenderAllStatesAsUserTaskWhenNoTerminalStates() {
    WorkflowDefinition workflow = new WorkflowDefinition() {
      @Override
      public String getCode() {
        return "no-terminal";
      }

      @Override
      public List<String> getStates() {
        return List.of("STEP1", "STEP2");
      }

      @Override
      public String getInitial() {
        return "STEP1";
      }

      @Override
      public List<String> getTerminalStates() {
        return List.of();
      }

      @Override
      public List<TransitionRule> getTransitions() {
        return List.of();
      }
    };

    StaticBpmnGenerator generator = new StaticBpmnGenerator();
    String xml = generator.generate(workflow);

    assertThat(xml).contains("<bpmn:userTask id=\"STEP1\"");
    assertThat(xml).contains("<bpmn:userTask id=\"STEP2\"");
    assertThat(xml).doesNotContain("<bpmn:endEvent");
  }

  @Test
  @DisplayName("shouldGenerateXmlWithNoSequenceFlowsWhenTransitionsEmpty")
  void shouldGenerateXmlWithNoSequenceFlowsWhenTransitionsEmpty() {
    WorkflowDefinition workflow = new WorkflowDefinition() {
      @Override
      public String getCode() {
        return "empty-transitions";
      }

      @Override
      public List<String> getStates() {
        return List.of("A", "B");
      }

      @Override
      public String getInitial() {
        return "A";
      }

      @Override
      public List<String> getTerminalStates() {
        return List.of("B");
      }

      @Override
      public List<TransitionRule> getTransitions() {
        return List.of();
      }
    };

    StaticBpmnGenerator generator = new StaticBpmnGenerator();
    String xml = generator.generate(workflow);

    // Only start→initial sequence flow, no transition flows
    assertThat(xml).contains("sf-start-A");
    assertThat(xml).doesNotContain("sf-A-B-");
  }

  @Test
  @DisplayName("shouldGenerateMultipleTransitionsWithCorrectEventCodes")
  void shouldGenerateMultipleTransitionsWithCorrectEventCodes() {
    EventDefinition approveEvent = simpleEvent("approve");
    EventDefinition rejectEvent = simpleEvent("reject");

    WorkflowDefinition workflow = new WorkflowDefinition() {
      @Override
      public String getCode() {
        return "multi-transition";
      }

      @Override
      public List<String> getStates() {
        return List.of("PENDING", "APPROVED", "REJECTED");
      }

      @Override
      public String getInitial() {
        return "PENDING";
      }

      @Override
      public List<String> getTerminalStates() {
        return List.of("APPROVED", "REJECTED");
      }

      @Override
      public List<TransitionRule> getTransitions() {
        return List.of(
            new TransitionRule("PENDING", "APPROVED", Action.APPROVE, approveEvent, "FAULTED", null),
            new TransitionRule("PENDING", "REJECTED", Action.REJECT, rejectEvent, "FAULTED", null));
      }
    };

    StaticBpmnGenerator generator = new StaticBpmnGenerator();
    String xml = generator.generate(workflow);

    assertThat(xml).contains("sf-PENDING-APPROVED-approve");
    assertThat(xml).contains("sf-PENDING-REJECTED-reject");
    assertThat(xml).contains("name=\"approve\"");
    assertThat(xml).contains("name=\"reject\"");
  }
}
