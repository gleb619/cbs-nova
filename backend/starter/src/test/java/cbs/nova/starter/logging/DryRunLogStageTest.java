package cbs.nova.starter.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.starter.core.pipe.DslPipeContext;
import cbs.nova.starter.core.pipe.DslPipeStage;
import cbs.nova.starter.core.stage.DryRunLogStage;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class DryRunLogStageTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final ThreadLocalDryRunLoggingContext context = new ThreadLocalDryRunLoggingContext();
  private final DryRunLogBufferRegistry registry = new DryRunLogBufferRegistry();
  private final DryRunLogbackAppender appender = new DryRunLogbackAppender(context, registry);
  private final Logger logger = (Logger) LoggerFactory.getLogger(DryRunLogStageTest.class);
  private final org.slf4j.Logger slf4jLogger = LoggerFactory.getLogger(DryRunLogStageTest.class);
  private Appender<ILoggingEvent> originalDryRunAppender;

  @BeforeEach
  void setUp() {
    context.clearRunId();
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    originalDryRunAppender = root.getAppender("DRY_RUN");
    if (originalDryRunAppender != null) {
      root.detachAppender(originalDryRunAppender);
    }
    appender.setContext(root.getLoggerContext());
    appender.setName("DRY_RUN");
    appender.start();
    root.addAppender(appender);
    root.setLevel(Level.INFO);
    logger.setLevel(Level.INFO);
  }

  @AfterEach
  void tearDown() {
    context.clearRunId();
    Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    root.detachAppender(appender);
    appender.stop();
    if (originalDryRunAppender != null) {
      root.addAppender(originalDryRunAppender);
    }
  }

  @Test
  void bufferIsRemovedFromRegistryAfterSuccessfulRun() {
    String runId = "run-success";
    DryRunLogStage stage = new DryRunLogStage(context, registry, 100);
    DslPipeContext ctx = new DslPipeContext("test", contextFactory.of("in", ExecutionMode.PREVIEW),
            ExecutionMode.PREVIEW, runId);

    Result<?> result = stage.execute(ctx, next -> {
      slf4jLogger.info("inside dry run");
      return Result.success("done");
    });

    assertThat(result.isSuccess()).isTrue();
    assertThat(registry.get(runId)).isNull();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> logs = ctx.getAttribute("dryRunLogs", List.class);
    assertThat(logs).hasSize(1);
  }

  @Test
  void bufferIsRemovedFromRegistryAfterException() {
    String runId = "run-exception";
    DryRunLogStage stage = new DryRunLogStage(context, registry, 100);
    DslPipeContext ctx = new DslPipeContext("test", contextFactory.of("in", ExecutionMode.PREVIEW),
            ExecutionMode.PREVIEW, runId);

    assertThatThrownBy(() -> stage.execute(ctx, next -> {
      slf4jLogger.info("before boom");
      throw new IllegalStateException("boom");
    })).isInstanceOf(IllegalStateException.class).hasMessage("boom");

    assertThat(registry.get(runId)).isNull();
    assertThat(context.currentRunId()).isNull();
  }

  @Test
  void sameRunIdStartedAgainBeginsWithEmptyBuffer() {
    String runId = "run-reuse";
    DryRunLogStage stage = new DryRunLogStage(context, registry, 100);

    DslPipeContext first = new DslPipeContext("test",
            contextFactory.of("in", ExecutionMode.PREVIEW), ExecutionMode.PREVIEW, runId);
    stage.execute(first, next -> {
      slf4jLogger.info("first run");
      return Result.success("first");
    });

    DslPipeContext second = new DslPipeContext("test",
            contextFactory.of("in", ExecutionMode.PREVIEW), ExecutionMode.PREVIEW, runId);
    stage.execute(second, next -> {
      slf4jLogger.info("second run");
      return Result.success("second");
    });

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> secondLogs = second.getAttribute("dryRunLogs", List.class);
    assertThat(secondLogs).hasSize(1);
    assertThat(secondLogs.get(0).get("message")).isEqualTo("second run");
  }

  @Test
  void sameRunIdAfterExceptionBeginsWithEmptyBuffer() {
    String runId = "run-reuse-after-exception";
    DryRunLogStage stage = new DryRunLogStage(context, registry, 100);

    DslPipeContext first = new DslPipeContext("test",
            contextFactory.of("in", ExecutionMode.PREVIEW), ExecutionMode.PREVIEW, runId);
    try {
      stage.execute(first, next -> {
        slf4jLogger.info("first run");
        throw new IllegalStateException("boom");
      });
    } catch (IllegalStateException ignored) {
      // expected
    }

    DslPipeContext second = new DslPipeContext("test",
            contextFactory.of("in", ExecutionMode.PREVIEW), ExecutionMode.PREVIEW, runId);
    stage.execute(second, next -> {
      slf4jLogger.info("second run");
      return Result.success("second");
    });

    @SuppressWarnings("unchecked")
    List<Map<String, Object>> secondLogs = second.getAttribute("dryRunLogs", List.class);
    assertThat(secondLogs).hasSize(1);
    assertThat(secondLogs.get(0).get("message")).isEqualTo("second run");
  }

  @Test
  void runModeSkipsBufferRegistration() {
    String runId = "run-mode";
    DryRunLogStage stage = new DryRunLogStage(context, registry, 100);
    DslPipeContext ctx = new DslPipeContext("test", contextFactory.of("in", ExecutionMode.RUN),
            ExecutionMode.RUN, runId);

    Result<?> result = stage.execute(ctx, next -> {
      slf4jLogger.info("run mode");
      return Result.success("done");
    });

    assertThat(result.isSuccess()).isTrue();
    assertThat(registry.get(runId)).isNull();
    assertThat(context.currentRunId()).isNull();
  }
}
