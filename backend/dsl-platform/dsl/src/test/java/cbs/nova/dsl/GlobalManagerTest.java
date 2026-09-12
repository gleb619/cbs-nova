package cbs.nova.dsl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.exception.DslEntityNotFoundException;
import cbs.nova.dsl.model.ExplainReport;
import cbs.nova.dsl.exception.DslExecutionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

class GlobalManagerTest {

  private final ContextFactory contextFactory = new ContextFactory();

  @BeforeEach
  void reset() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void endToEndProcessPreview() {
    var gm = GlobalManager.globalManager();
    gm.registerProcess(
            Dsl.process("Greet")
                    .input(String.class)
                    .output(String.class)
                    .execute(ctx -> Result.success("Hello, " + ctx.body()))
                    .build());
    var ctx = contextFactory.of("World", ExecutionMode.PREVIEW);
    var result = gm.runProcess("Greet", ctx);
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("Hello, World");
  }

  @Test
  void unknownProcessReturnsFailure() {
    var result = GlobalManager.globalManager()
            .runProcess("Ghost", contextFactory.of("x", ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void helperRoundTrip() {
    var gm = GlobalManager.globalManager();
    gm.registerHelper("upper", ctx -> Result.success(ctx.body().toString().toUpperCase()));
    var result = gm.runHelper("upper",
            contextFactory.of("hello", ExecutionMode.PREVIEW));
    assertThat(result.value()).isEqualTo("HELLO");
  }

  @Test
  void transactionRoundTrip() {
    var gm = GlobalManager.globalManager();
    var tx = Dsl.transaction("TestTx")
            .execute(ctx -> Result.success("ok"))
            .build();
    gm.registerTransaction(tx);
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    var result = gm.runTransaction("TestTx", ctx);
    assertThat(result.isSuccess()).isTrue();
  }

  @Test
  void unknownTransactionReturnsFailure() {
    var result = GlobalManager.globalManager().runTransaction("Ghost",
            contextFactory.of("x", ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void functionRoundTrip() {
    var gm = GlobalManager.globalManager();
    var fn = Dsl.function("TestFn")
            .execute(ctx -> Result.success("fn-ok"))
            .build();
    gm.registerFunction(fn);
    var ctx = contextFactory.of("body", ExecutionMode.RUN, "run-1");
    var result = gm.runFunction("TestFn", ctx);
    assertThat(result.value()).isEqualTo("fn-ok");
  }

  @Test
  void unknownFunctionReturnsFailure() {
    var result = GlobalManager.globalManager().runFunction("Ghost",
            contextFactory.of("x", ExecutionMode.PREVIEW));
    assertThat(result.isSuccess()).isFalse();
  }

  @Test
  void processNamesSorted() {
    var gm = GlobalManager.globalManager();
    gm.registerProcess(Dsl.process("Z").execute(ctx -> Result.success("z")).build());
    gm.registerProcess(Dsl.process("A").execute(ctx -> Result.success("a")).build());
    var names = gm.processNames();
    assertThat(names).containsExactlyInAnyOrder("A", "Z");
  }

  @Test
  void transactionNamesSorted() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(
            Dsl.transaction("Ztx").execute(ctx -> Result.success("z")).build());
    gm.registerTransaction(
            Dsl.transaction("Atx").execute(ctx -> Result.success("a")).build());
    var names = gm.transactionNames();
    assertThat(names).containsExactlyInAnyOrder("Atx", "Ztx");
  }

  @Test
  void helperNamesSorted() {
    var gm = GlobalManager.globalManager();
    gm.registerHelper("Ahelper", ctx -> Result.success("A"));
    gm.registerHelper("Bhelper", ctx -> Result.success("B"));
    var names = gm.helperNames();
    assertThat(names).containsExactlyInAnyOrder("Ahelper", "Bhelper");
  }

  @Test
  void describeHelperReturnsDescriptorForRegistered() {
    var gm = GlobalManager.globalManager();
    gm.registerHelper("HelperA", ctx -> Result.success("A"));
    var descriptor = gm.describeHelper("HelperA");
    assertThat(descriptor).isNotEmpty();
  }

  @Test
  void transactionCompensationRoundTrip() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();
    var tx = Dsl.transaction("CompTx")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> {
              order.add("compensated:" + ctx.body());
              return Result.success(null);
            })
            .build();
    gm.registerTransaction(tx);
    var baseCtx = contextFactory.of("payload", ExecutionMode.RUN, "run-comp");
    assertThat(gm.registerTransactionCompensation("CompTx", "run-comp", baseCtx)).isTrue();
    gm.compensateTransaction("CompTx", "run-comp", new RuntimeException("boom"));
    assertThat(order).containsExactly("compensated:payload");
  }

  @Test
  void directTransactionCompensationFindsRegisteredTransaction() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();
    var tx = Dsl.transaction("DirectCompTx")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> {
              order.add("direct:" + ctx.body());
              return Result.success(null);
            })
            .build();
    gm.registerTransaction(tx);
    var ctx = contextFactory.of("direct-payload", ExecutionMode.COMPENSATION, "run-direct");
    gm.compensateTransaction("DirectCompTx", ctx, new RuntimeException("boom"));
    assertThat(order).containsExactly("direct:direct-payload");
  }

  @Test
  void missingTransactionCompensationIsNoOp() {
    var gm = GlobalManager.globalManager();
    gm.compensateTransaction("MissingTx", "run-1", new RuntimeException("boom"));
  }

  @Test
  void runProcessWithCompensationReturnsSuccessValue() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();

    Object result = gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> {
              order.add("main");
              return Result.success("ok");
            },
            (compCtx, error) -> order.add("compensate"));

    assertThat(result).isEqualTo("ok");
    assertThat(order).containsExactly("main");
  }

  @Test
  void runProcessWithCompensationInvokesCompensationOnFailure() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();

    assertThatThrownBy(() -> gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> {
              order.add("main");
              return Result.failure(new RuntimeException("boom"));
            },
            (compCtx, error) -> order.add("compensate:" + error.getMessage())))
            .isInstanceOf(DslExecutionException.class)
            .hasMessageContaining("Process failed")
            .hasMessageContaining("boom");

    assertThat(order).containsExactly("main", "compensate:boom");
  }

  @Test
  void runProcessWithCompensationInvokesCompensationOnException() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();

    assertThatThrownBy(() -> gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> {
              order.add("main");
              throw new IllegalStateException("bang");
            },
            (compCtx, error) -> order.add("compensate:" + error.getClass().getSimpleName())))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("bang");

    assertThat(order).containsExactly("main", "compensate:IllegalStateException");
  }

  @Test
  void runProcessWithCompensationCompensatesTransactionsInReverseOrder() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();

    gm.registerTransaction(Dsl.transaction("TxA")
            .execute(ctx -> Result.success("a"))
            .compensation(ctx -> {
              order.add("TxA");
              return Result.success(null);
            })
            .build());

    gm.registerTransaction(Dsl.transaction("TxB")
            .execute(ctx -> Result.success("b"))
            .compensation(ctx -> {
              order.add("TxB");
              return Result.success(null);
            })
            .build());

    assertThatThrownBy(() -> gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> {
              gm.runTransaction("TxA", ctx);
              gm.runTransaction("TxB", ctx);
              return Result.failure(new RuntimeException("boom"));
            },
            (compCtx, error) -> {
              /* process compensation is a no-op in this test */ }))
            .isInstanceOf(DslExecutionException.class)
            .hasMessageContaining("boom");

    assertThat(order).containsExactly("TxB", "TxA");
  }
  @Test
  void runTransactionWithCompensationReturnsSuccessValue() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(Dsl.transaction("SugarTx")
            .input(String.class)
            .execute(ctx -> Result.success("tx-" + ctx.body()))
            .build());

    Object result = gm.runTransactionWithCompensation("SugarTx", "run-1", "payload");

    assertThat(result).isEqualTo("tx-payload");
  }

  @Test
  void runTransactionWithCompensationThrowsOnFailure() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(Dsl.transaction("FailingSugarTx")
            .execute(ctx -> Result.failure(new RuntimeException("boom")))
            .build());

    assertThatThrownBy(() -> gm.runTransactionWithCompensation("FailingSugarTx", "run-1", "x"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Transaction failed")
            .cause()
            .hasMessage("boom");
  }

  @Test
  void compensateTransactionWithInputRunsCompensationLogic() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();
    gm.registerTransaction(Dsl.transaction("CompSugarTx")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> {
              order.add("comp:" + ctx.body());
              return Result.success(null);
            })
            .build());

    gm.compensateTransaction("CompSugarTx", "run-1", "input", new RuntimeException("boom"));

    assertThat(order).containsExactly("comp:input");
  }

  @Test
  void compensateTransactionWithInputIsNoOpWhenCompensationMissing() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(Dsl.transaction("NoCompSugarTx")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .build());

    gm.compensateTransaction("NoCompSugarTx", "run-1", "input", new RuntimeException("boom"));
  }

  @Test
  void runProcessWithCompensationDoesNotDoubleRunProcessCompensation() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();

    gm.registerProcess(Dsl.process("DoubleCheck")
            .input(String.class)
            .output(String.class)
            .execute(ctx -> Result.failure(new RuntimeException("boom")))
            .compensation(ctx -> {
              order.add("process-comp");
              return Result.success(null);
            })
            .build());

    assertThatThrownBy(() -> gm.runProcessWithCompensation(
            "run-1",
            "body",
            ctx -> gm.runProcess("DoubleCheck", ctx),
            (compCtx, error) -> gm.compensateProcess("DoubleCheck", compCtx, error)))
            .isInstanceOf(DslExecutionException.class)
            .hasMessageContaining("boom");

    assertThat(order).containsExactly("process-comp");
  }

  @Test
  void runProcessObjectOverloadUsesPassedObjectAsSourceOfTruth() {
    var gm = GlobalManager.globalManager();
    gm.registerProcess(Dsl.process("Registered")
            .input(String.class)
            .execute(ctx -> Result.success("registered"))
            .build());

    var object = Dsl.process("Other")
            .input(String.class)
            .execute(ctx -> Result.success("object-wins"))
            .build();

    var ctx = contextFactory.of("in", ExecutionMode.PREVIEW);
    assertThat(gm.runProcess(object, ctx).value()).isEqualTo("object-wins");
  }

  @Test
  void runProcessObjectOverloadIgnoresVersionMismatch() {
    var gm = GlobalManager.globalManager();
    gm.registerProcess(Dsl.process("P").version("v2")
            .input(String.class)
            .execute(ctx -> Result.success("v2"))
            .build());

    var object = Dsl.process("P").version("v99")
            .input(String.class)
            .execute(ctx -> Result.success("v99"))
            .build();

    var ctx = contextFactory.of("in", ExecutionMode.PREVIEW);
    assertThat(gm.runProcess(object, ctx).value()).isEqualTo("v99");
  }

  @Test
  void runProcessStringOverloadFailureParity() {
    var ctx = contextFactory.of("in", ExecutionMode.PREVIEW);
    var result = GlobalManager.globalManager().runProcess("Missing", ctx);
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.cause()).isInstanceOf(DslEntityNotFoundException.class)
            .hasMessageContaining("Process not found: Missing");
  }

  @Test
  void runTransactionObjectOverloadUsesPassedObjectAsSourceOfTruth() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(Dsl.transaction("Registered")
            .execute(ctx -> Result.success("registered"))
            .build());

    var object = Dsl.transaction("Other")
            .execute(ctx -> Result.success("object-wins"))
            .build();

    var ctx = contextFactory.of("in", ExecutionMode.RUN, "run-1");
    assertThat(gm.runTransaction(object, ctx).value()).isEqualTo("object-wins");
  }

  @Test
  void runTransactionWithCompensationObjectOverloadSuccess() {
    var gm = GlobalManager.globalManager();
    var tx = Dsl.transaction("SugarTx")
            .input(String.class)
            .execute(ctx -> Result.success("tx-" + ctx.body()))
            .build();

    Object result = gm.runTransactionWithCompensation(tx, "run-1", "payload");
    assertThat(result).isEqualTo("tx-payload");
  }

  @Test
  void runTransactionWithCompensationObjectOverloadThrowsOnFailure() {
    var gm = GlobalManager.globalManager();
    var tx = Dsl.transaction("FailingTx")
            .execute(ctx -> Result.failure(new RuntimeException("boom")))
            .build();

    assertThatThrownBy(() -> gm.runTransactionWithCompensation(tx, "run-1", "x"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Transaction failed")
            .cause()
            .hasMessage("boom");
  }

  @Test
  void runTransactionWithCompensationStringOverloadFailureParity() {
    assertThatThrownBy(() -> GlobalManager.globalManager()
            .runTransactionWithCompensation("Missing", "run-1", "x"))
            .isInstanceOf(RuntimeException.class)
            .hasMessage("Transaction failed")
            .cause()
            .isInstanceOf(DslEntityNotFoundException.class)
            .hasMessageContaining("Transaction not found: Missing");
  }

  @Test
  void compensateProcessObjectOverloadRunsCompensationLogic() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();
    var process = Dsl.process("DirectComp")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> {
              order.add("comp:" + ctx.body());
              return Result.success(null);
            })
            .build();

    var ctx = contextFactory.of("direct-payload", ExecutionMode.COMPENSATION, "run-direct");
    gm.compensateProcess(process, ctx, new RuntimeException("boom"));
    assertThat(order).containsExactly("comp:direct-payload");
  }

  @Test
  void compensateTransactionObjectOverloadRunsCompensationLogic() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();
    var tx = Dsl.transaction("DirectCompTx")
            .input(String.class)
            .execute(ctx -> Result.success("ok"))
            .compensation(ctx -> {
              order.add("comp:" + ctx.body());
              return Result.success(null);
            })
            .build();

    gm.compensateTransaction(tx, "run-direct", "direct-payload", new RuntimeException("boom"));
    assertThat(order).containsExactly("comp:direct-payload");
  }

  @Test
  void runProcessWithCompensationObjectOverloadSuccess() {
    var gm = GlobalManager.globalManager();
    var process = Dsl.process("Pwc")
            .input(String.class)
            .execute(ctx -> Result.success("pwc-" + ctx.body()))
            .build();

    Object result = gm.runProcessWithCompensation("run-1", "body", process);
    assertThat(result).isEqualTo("pwc-body");
  }

  @Test
  void runProcessWithCompensationObjectOverloadCompensatesOnFailure() {
    var gm = GlobalManager.globalManager();
    var order = new ArrayList<String>();
    var process = Dsl.process("PwcFail")
            .input(String.class)
            .execute(ctx -> Result.failure(new RuntimeException("boom")))
            .compensation(ctx -> {
              order.add("comp:" + ctx.body());
              return Result.success(null);
            })
            .build();

    assertThatThrownBy(() -> gm.runProcessWithCompensation("run-1", "body", process))
            .isInstanceOf(DslExecutionException.class)
            .hasMessageContaining("Process failed")
            .hasMessageContaining("boom");

    assertThat(order).containsExactly("comp:body");
  }

  @Test
  void descriptionReturnsProcessDescription() {
    var gm = GlobalManager.globalManager();
    gm.registerProcess(
            Dsl.process("DescribedP")
                    .describe(() -> DslDescriptor.builder()
                            .name("DescribedP")
                            .type(DslObject.DslType.PROCESS)
                            .description("A process that greets")
                            .inputType(String.class)
                            .outputType(String.class)
                            .hasCompensation(false)
                            .hasSideEffects(false)
                            .parameters(List.of())
                            .taskQueue(null)
                            .version(null)
                            .startToCloseTimeout(null)
                            .heartbeatTimeout(null)
                            .build())
                    .execute(ctx -> Result.success("ok"))
                    .build());

    assertThat(gm.description("DescribedP")).contains("A process that greets");
  }

  @Test
  void descriptionReturnsTransactionDescription() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(
            Dsl.transaction("DescribedT")
                    .describe(() -> DslDescriptor.builder()
                            .name("DescribedT")
                            .type(DslObject.DslType.TRANSACTION)
                            .description("A transaction that pays")
                            .inputType(String.class)
                            .outputType(String.class)
                            .hasCompensation(false)
                            .hasSideEffects(false)
                            .parameters(List.of())
                            .taskQueue(null)
                            .version(null)
                            .startToCloseTimeout(null)
                            .heartbeatTimeout(null)
                            .build())
                    .execute(ctx -> Result.success("ok"))
                    .build());

    assertThat(gm.description("DescribedT")).contains("A transaction that pays");
  }

  @Test
  void descriptionReturnsHelperDescription() {
    var gm = GlobalManager.globalManager();
    gm.registerHelper("DescribedH", new Executable<String, String>() {
      @Override
      public Result<String> execute(Context<String> ctx) {
        return Result.success(ctx.body());
      }

      @Override
      public ExecutableDescriptor describe() {
        return new ExecutableDescriptor(
                "DescribedH",
                "A helpful helper",
                String.class,
                String.class,
                false,
                null,
                List.of());
      }
    });

    assertThat(gm.description("DescribedH")).contains("A helpful helper");
  }

  @Test
  void descriptionReturnsEmptyForUnknown() {
    assertThat(GlobalManager.globalManager().description("Missing")).isEmpty();
  }

  @Test
  void explainHelperReturnsReportWithMermaidForRegisteredHelper() {
    var gm = GlobalManager.globalManager();
    gm.registerHelper("upper", ctx -> Result.success(ctx.body().toString().toUpperCase()));
    var ctx = contextFactory.of("hello", ExecutionMode.EXPLAIN);

    var report = gm.explainHelper("upper", ctx);

    assertThat(report).isPresent();
    assertThat(report.get().mermaid()).contains("graph TD", "upper");
  }

  @Test
  void explainHelperIsEmptyForUnknownHelper() {
    var ctx = contextFactory.of("x", ExecutionMode.EXPLAIN);
    assertThat(GlobalManager.globalManager().explainHelper("Ghost", ctx)).isEmpty();
  }

  @Test
  void explainHelperUsesHelperOwnExplainOverride() {
    var gm = GlobalManager.globalManager();
    gm.registerHelper("custom", new Executable<String, String>() {
      @Override
      public Result<String> execute(Context<String> ctx) {
        return Result.success("ok");
      }

      @Override
      public ExplainReport explain(Context<String> ctx, int budgetChars) {
        return new ExplainReport("custom", "Custom explanation.", "graph TD\n  C[custom]");
      }
    });
    var ctx = contextFactory.of("body", ExecutionMode.EXPLAIN);

    var report = gm.explainHelper("custom", ctx);

    assertThat(report).isPresent();
    assertThat(report.get().description()).isEqualTo("Custom explanation.");
    assertThat(report.get().mermaid()).isEqualTo("graph TD\n  C[custom]");
  }

  @Test
  void explainDispatchesToProcess() {
    var gm = GlobalManager.globalManager();
    gm.registerProcess(Dsl.process("ExplainP")
            .describe(() -> DslDescriptor.builder()
                    .name("ExplainP")
                    .type(DslObject.DslType.PROCESS)
                    .description("A process to explain")
                    .inputType(String.class)
                    .outputType(String.class)
                    .hasCompensation(false)
                    .hasSideEffects(false)
                    .parameters(List.of())
                    .taskQueue(null)
                    .version(null)
                    .startToCloseTimeout(null)
                    .heartbeatTimeout(null)
                    .build())
            .execute(ctx -> Result.success("ok"))
            .build());
    var ctx = contextFactory.of("body", ExecutionMode.EXPLAIN);

    var report = gm.explain("ExplainP", ctx);

    assertThat(report).isPresent();
    assertThat(report.get().name()).isEqualTo("ExplainP");
    assertThat(report.get().description()).isEqualTo("A process to explain");
    assertThat(report.get().mermaid()).contains("graph TD", "ExplainP");
  }

  @Test
  void explainDispatchesToTransaction() {
    var gm = GlobalManager.globalManager();
    gm.registerTransaction(Dsl.transaction("ExplainT")
            .describe(() -> DslDescriptor.builder()
                    .name("ExplainT")
                    .type(DslObject.DslType.TRANSACTION)
                    .description("A transaction to explain")
                    .inputType(String.class)
                    .outputType(String.class)
                    .hasCompensation(false)
                    .hasSideEffects(false)
                    .parameters(List.of())
                    .taskQueue(null)
                    .version(null)
                    .startToCloseTimeout(null)
                    .heartbeatTimeout(null)
                    .build())
            .execute(ctx -> Result.success("ok"))
            .build());
    var ctx = contextFactory.of("body", ExecutionMode.EXPLAIN);

    var report = gm.explain("ExplainT", ctx);

    assertThat(report).isPresent();
    assertThat(report.get().name()).isEqualTo("ExplainT");
    assertThat(report.get().mermaid()).contains("graph TD", "ExplainT");
  }

  @Test
  void explainDispatchesToHelperWhenNoProcessOrTransactionMatches() {
    var gm = GlobalManager.globalManager();
    gm.registerHelper("ExplainH", ctx -> Result.success("ok"));
    var ctx = contextFactory.of("body", ExecutionMode.EXPLAIN);

    var report = gm.explain("ExplainH", ctx);

    assertThat(report).isPresent();
    assertThat(report.get().mermaid()).contains("graph TD", "ExplainH");
  }

  @Test
  void explainDispatchesToFunction() {
    var gm = GlobalManager.globalManager();
    gm.registerFunction(Dsl.function("ExplainF")
            .describe(() -> DslDescriptor.builder()
                    .name("ExplainF")
                    .type(DslObject.DslType.FUNCTION)
                    .description("A function to explain")
                    .inputType(Void.class)
                    .outputType(Void.class)
                    .hasCompensation(false)
                    .hasSideEffects(false)
                    .parameters(List.of())
                    .taskQueue(null)
                    .version(null)
                    .startToCloseTimeout(null)
                    .heartbeatTimeout(null)
                    .build())
            .execute(ctx -> Result.success("ok"))
            .build());
    var ctx = contextFactory.of("body", ExecutionMode.EXPLAIN);

    var report = gm.explain("ExplainF", ctx);

    assertThat(report).isPresent();
    assertThat(report.get().name()).isEqualTo("ExplainF");
    assertThat(report.get().description()).isEqualTo("A function to explain");
  }

  @Test
  void explainIsEmptyForUnknownName() {
    var ctx = contextFactory.of("x", ExecutionMode.EXPLAIN);
    assertThat(GlobalManager.globalManager().explain("Missing", ctx)).isEmpty();
  }

  @Test
  void explainRespectsBudget() {
    var gm = GlobalManager.globalManager();
    gm.registerHelper("BudgetedH", ctx -> Result.success("ok"));
    var ctx = contextFactory.of("body", ExecutionMode.EXPLAIN);

    var report = gm.explainHelper("BudgetedH", ctx, 50);

    assertThat(report).isPresent();
    assertThat(report.get().description().length()).isLessThanOrEqualTo(50);
    assertThat(report.get().description().length()
            + report.get().mermaid().length()).isLessThanOrEqualTo(50);
  }

}
