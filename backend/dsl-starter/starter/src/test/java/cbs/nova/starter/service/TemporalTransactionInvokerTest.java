package cbs.nova.starter.service;

import cbs.nova.dsl.model.SimpleContext;
import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Dsl;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
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

    var ctx = SimpleContext.builder("input").mode(ExecutionMode.RUN).runId("run-1").build();
    Result<?> result = invoker.invoke("FallbackTx", "input", ctx);

    assertThat(result.isSuccess()).isTrue();
    assertThat(result.value()).isEqualTo("local-ok");
    assertThat(listAppender.list)
            .anyMatch(e -> e.getLevel() == Level.WARN
                    && e.getFormattedMessage().contains("FallbackTx"));
  }

  @Test
  void fallbackWhenNeitherRegistered() {
    var ctx = SimpleContext.builder("input").mode(ExecutionMode.RUN).runId("run-1").build();
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
  void buildActivityOptionsAppliesMaximumIntervalWhenSet() {
    // T751: a configured maximumInterval must reach Temporal so backoff does not
    // grow beyond the configured cap on long retry chains.
    var policy = new RetryPolicy(5, Duration.ofSeconds(1), 2.0)
            .withMaximumInterval(Duration.ofSeconds(30));
    var tx = Dsl.transaction("CappedRetryTx")
            .execute(ctx -> Result.success("ok"))
            .startToCloseTimeout(Duration.ofMinutes(5))
            .retryPolicy(policy)
            .build();

    RetryOptions ro = invoker.buildActivityOptions(tx).getRetryOptions();

    assertThat(ro.getMaximumInterval()).isEqualTo(Duration.ofSeconds(30));
  }

  @Test
  void buildActivityOptionsOmitsMaximumIntervalWhenUnset() {
    // T751: when maximumInterval is null the Temporal default cap is left
    // untouched, preserving the prior behaviour for every existing DSL flow.
    var policy = new RetryPolicy(5, Duration.ofSeconds(1), 2.0);
    var tx = Dsl.transaction("DefaultCapRetryTx")
            .execute(ctx -> Result.success("ok"))
            .startToCloseTimeout(Duration.ofMinutes(5))
            .retryPolicy(policy)
            .build();

    RetryOptions ro = invoker.buildActivityOptions(tx).getRetryOptions();

    assertThat(ro.getMaximumInterval()).isNull();
  }

  @Test
  void buildActivityOptionsAppliesDoNotRetryWhenSet() {
    // T751: declared non-retryable error types must reach Temporal's
    // RetryOptions#setDoNotRetry so a deterministic validation failure does
    // not consume the retry budget.
    var policy = new RetryPolicy(5, Duration.ofSeconds(1), 2.0)
            .withNonRetryable("ValidationException", "BadRequest");
    var tx = Dsl.transaction("NoDoRetryTx")
            .execute(ctx -> Result.success("ok"))
            .startToCloseTimeout(Duration.ofMinutes(5))
            .retryPolicy(policy)
            .build();

    RetryOptions ro = invoker.buildActivityOptions(tx).getRetryOptions();

    assertThat(ro.getDoNotRetry()).containsExactly("ValidationException", "BadRequest");
  }

  @Test
  void buildActivityOptionsOmitsDoNotRetryWhenUnset() {
    // T751: when nonRetryable is null the Temporal default retry-on-everything
    // behaviour is left untouched, preserving the prior behaviour.
    var policy = new RetryPolicy(5, Duration.ofSeconds(1), 2.0);
    var tx = Dsl.transaction("DefaultDoRetryTx")
            .execute(ctx -> Result.success("ok"))
            .startToCloseTimeout(Duration.ofMinutes(5))
            .retryPolicy(policy)
            .build();

    RetryOptions ro = invoker.buildActivityOptions(tx).getRetryOptions();

    assertThat(ro.getDoNotRetry()).isNull();
  }

  @Test
  void buildActivityOptionsOmitsDoNotRetryWhenEmptyList() {
    // T751: an empty nonRetryable list must not call setDoNotRetry; an empty
    // String[] would override the Temporal default and re-enable retrying
    // everything.
    var policy = new RetryPolicy(5, Duration.ofSeconds(1), 2.0)
            .withNonRetryable(java.util.List.of());
    var tx = Dsl.transaction("EmptyDoRetryTx")
            .execute(ctx -> Result.success("ok"))
            .startToCloseTimeout(Duration.ofMinutes(5))
            .retryPolicy(policy)
            .build();

    RetryOptions ro = invoker.buildActivityOptions(tx).getRetryOptions();

    assertThat(ro.getDoNotRetry()).isNull();
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

  @Test
  void buildActivityOptionsAppliesHeartbeatTimeoutWhenSet() {
    // T750: heartbeatTimeout declared in the DSL must reach Temporal's
    // ActivityOptions so a stuck activity is cancelled.
    var hb = Duration.ofSeconds(7);
    var tx = Dsl.transaction("HeartbeatTx")
            .execute(ctx -> Result.success("ok"))
            .startToCloseTimeout(Duration.ofSeconds(30))
            .heartbeatTimeout(hb)
            .build();

    ActivityOptions options = invoker.buildActivityOptions(tx);

    assertThat(options.getHeartbeatTimeout()).isEqualTo(hb);
  }

  @Test
  void buildActivityOptionsOmitsHeartbeatTimeoutWhenUnset() {
    // T750: a null heartbeatTimeout must leave Temporal's default untouched;
    // ActivityOptions#getHeartbeatTimeout reports null for "unset".
    var tx = Dsl.transaction("NoHeartbeatTx")
            .execute(ctx -> Result.success("ok"))
            .startToCloseTimeout(Duration.ofSeconds(30))
            .build();

    ActivityOptions options = invoker.buildActivityOptions(tx);

    assertThat(options.getHeartbeatTimeout()).isNull();
  }
}
