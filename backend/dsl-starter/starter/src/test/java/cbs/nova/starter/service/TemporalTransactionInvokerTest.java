package cbs.nova.starter.service;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.ContextFactory;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.model.RetryPolicy;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Duration;

class TemporalTransactionInvokerTest {

  private final ContextFactory contextFactory = new ContextFactory();
  private final TemporalTransactionInvoker invoker = new TemporalTransactionInvoker();
  private ListAppender<ILoggingEvent> listAppender;
  private Logger logger;

  @BeforeEach
  void setUp() {
    GlobalManager.globalManager().resetForTests();

    logger = (Logger) LoggerFactory.getLogger(TemporalTransactionInvoker.class);
    listAppender = new ListAppender<>();
    listAppender.start();
    logger.addAppender(listAppender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(listAppender);
    listAppender.stop();
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void fallbackWhenGeneratedDescriptorMissing() {
    var tx = Dsl.transaction("FallbackTx")
            .execute(ctx -> Result.success("local-ok"))
            .build();
    GlobalManager.globalManager().registerTransaction(tx);

    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-1");
    Result<?> result = invoker.invoke("FallbackTx", "input", ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("local-ok");
    assertThat(listAppender.list)
            .anyMatch(e -> e.getLevel() == Level.WARN
                    && e.getFormattedMessage().contains("FallbackTx"));
  }

  @Test
  void fallbackWhenNeitherRegistered() {
    var ctx = contextFactory.of("input", ExecutionMode.RUN, "run-1");
    Result<?> result = invoker.invoke("GhostTx", "input", ctx);

    assertThat(result.isSuccess()).isFalse();
    assertThat(listAppender.list)
            .anyMatch(e -> e.getLevel() == Level.WARN
                    && e.getFormattedMessage().contains("GhostTx"));
  }

  @Test
  void buildActivityOptionsUsesCustomRetryPolicy() {
    var customRetry = new RetryPolicy(5, Duration.ofSeconds(2), 3.0);
    var tx = Dsl.transaction("RetryTx")
            .execute(ctx -> Result.success("ok"))
            .startToCloseTimeout(Duration.ofSeconds(30))
            .retryPolicy(customRetry)
            .taskQueue("my-queue")
            .build();

    ActivityOptions options = invoker.buildActivityOptions(tx);
    RetryOptions ro = options.getRetryOptions();

    assertThat(ro.getMaximumAttempts()).isEqualTo(5);
    assertThat(ro.getInitialInterval()).isEqualTo(Duration.ofSeconds(2));
    assertThat(ro.getBackoffCoefficient()).isEqualTo(3.0);
  }

  @Test
  void buildActivityOptionsFallsBackToDefaultRetryPolicy() {
    var tx = Dsl.transaction("DefaultRetryTx")
            .execute(ctx -> Result.success("ok"))
            .startToCloseTimeout(Duration.ofSeconds(30))
            .taskQueue("my-queue")
            .build();

    ActivityOptions options = invoker.buildActivityOptions(tx);
    RetryOptions ro = options.getRetryOptions();
    RetryPolicy defaultPolicy = DslConfig.dslConfig().defaultRetryPolicy();

    assertThat(ro.getMaximumAttempts()).isEqualTo(defaultPolicy.maxAttempts());
    assertThat(ro.getInitialInterval()).isEqualTo(defaultPolicy.initialInterval());
    assertThat(ro.getBackoffCoefficient()).isEqualTo(defaultPolicy.backoffCoefficient());
  }

  @Test
  void buildActivityOptionsCarriesTimeoutAndTaskQueue() {
    var expectedTimeout = Duration.ofSeconds(42);
    var expectedQueue = "test-queue";
    var tx = Dsl.transaction("TimeoutTx")
            .execute(ctx -> Result.success("ok"))
            .startToCloseTimeout(expectedTimeout)
            .taskQueue(expectedQueue)
            .build();

    ActivityOptions options = invoker.buildActivityOptions(tx);

    assertThat(options.getStartToCloseTimeout()).isEqualTo(expectedTimeout);
    assertThat(options.getTaskQueue()).isEqualTo(expectedQueue);
  }
}
