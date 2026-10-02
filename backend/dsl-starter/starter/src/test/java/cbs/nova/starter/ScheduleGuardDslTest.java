package cbs.nova.starter;

import static org.assertj.core.api.Assertions.assertThat;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.ExecutionMode;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.dsl.helper.HelperInstanceResolver;
import cbs.nova.dsl.model.SimpleContext;
import cbs.nova.dsl.utils.DefinitionLoader;
import cbs.nova.dslexamples.v1.ScheduleGuardModels.ScheduleGuardIn;
import cbs.nova.dslexamples.v1.ScheduleGuardModels.ScheduleGuardOut;
import cbs.nova.starter.helper.CompensationTrackerHelper;
import cbs.nova.starter.helper.CronParseHelper;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * T764 smoke tests for the {@code ScheduleGuardDsl} example.
 *
 * <p>
 * Mirrors the {@link AdvancedDslExamplesTest} pattern: a fixed {@code after} timestamp removes
 * wall-clock dependence, so every assertion is reproducible regardless of when the test runs. The
 * {@code cronParse} helper is reachable through the resolver's reflective fallback (it has a
 * default constructor and implements {@code Executable}), so no custom resolver wiring is required
 * for this test.
 */
class ScheduleGuardDslTest {

  @BeforeEach
  void loadCompactDsls() {
    GlobalManager.globalManager().resetForTests();
    DslConfig.dslConfig().helperInstanceResolver().replace(typedHelperResolver());
    DslConfig.dslConfig().expressionEvaluator()
            .replace(new cbs.nova.dsl.utils.MvelExpressionEvaluator());
    new DefinitionLoader().load(GlobalManager.globalManager());
    GlobalManager.globalManager().registerHelperResolvers();
  }

  @AfterEach
  void cleanup() {
    GlobalManager.globalManager().resetForTests();
  }

  @Test
  void scheduleGuardPreviewProducesNextFireTimesForSixFieldExpression() {
    // 6-field DST-safe form: "sec min hour dom mon dow".
    // "0 0 12 * * *" fires daily at 12:00:00 UTC.
    // Fixed 'after' = 2024-01-15T00:00:00Z → next fires at 12:00:00 each day.
    var input = new ScheduleGuardIn(
            "daily-noon",
            "0 0 12 * * *",
            "2024-01-15T00:00:00Z",
            3);
    Context<ScheduleGuardIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("ScheduleGuard", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ScheduleGuardOut out = (ScheduleGuardOut) result.value();
    assertThat(out.scheduleName()).isEqualTo("daily-noon");
    assertThat(out.expression()).isEqualTo("0 0 12 * * *");
    assertThat(out.valid()).isTrue();
    assertThat(out.errorMessage()).isNull();
    assertThat(out.fireTimes()).containsExactly(
            "2024-01-15T12:00:00Z",
            "2024-01-16T12:00:00Z",
            "2024-01-17T12:00:00Z");
  }

  @Test
  void scheduleGuardPreviewProducesNextFireTimesForFiveFieldExpression() {
    // 5-field Unix form: "min hour dom mon dow" — the helper prepends "0 " seconds.
    // "30 9 * * *" fires daily at 09:30:00 UTC.
    var input = new ScheduleGuardIn(
            "morning-half",
            "30 9 * * *",
            "2024-01-15T00:00:00Z",
            2);
    Context<ScheduleGuardIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("ScheduleGuard", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ScheduleGuardOut out = (ScheduleGuardOut) result.value();
    assertThat(out.valid()).isTrue();
    assertThat(out.errorMessage()).isNull();
    assertThat(out.fireTimes()).containsExactly(
            "2024-01-15T09:30:00Z",
            "2024-01-16T09:30:00Z");
  }

  @Test
  void scheduleGuardPreviewHandlesAtDailyMacro() {
    // @daily is the "every day at midnight" macro.
    var input = new ScheduleGuardIn(
            "daily-midnight",
            "@daily",
            "2024-01-15T00:00:00Z",
            2);
    Context<ScheduleGuardIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("ScheduleGuard", ctx);

    assertThat(result.isSuccess()).as("cause: %s", result.cause()).isTrue();
    ScheduleGuardOut out = (ScheduleGuardOut) result.value();
    assertThat(out.valid()).isTrue();
    assertThat(out.errorMessage()).isNull();
    assertThat(out.fireTimes()).containsExactly(
            "2024-01-16T00:00:00Z",
            "2024-01-17T00:00:00Z");
  }

  @Test
  void scheduleGuardPreviewFailsWithErrorMessageForInvalidExpression() {
    // Out-of-range day-of-month: 32 is invalid in every cron variant.
    var input = new ScheduleGuardIn(
            "bad-expr",
            "0 0 12 32 * *",
            "2024-01-15T00:00:00Z",
            3);
    Context<ScheduleGuardIn> ctx = SimpleContext.builder(input).mode(ExecutionMode.PREVIEW)
            .build();

    Result<?> result = GlobalManager.globalManager().runProcess("ScheduleGuard", ctx);

    // The DSL itself succeeds — the guard surfaces the failure as a structured result
    // with valid=false and an error message, so the caller can decide how to route it.
    assertThat(result.isSuccess()).isTrue();
    ScheduleGuardOut out = (ScheduleGuardOut) result.value();
    assertThat(out.scheduleName()).isEqualTo("bad-expr");
    assertThat(out.expression()).isEqualTo("0 0 12 32 * *");
    assertThat(out.valid()).isFalse();
    assertThat(out.fireTimes()).isNull();
    assertThat(out.errorMessage()).isNotBlank();
  }

  private static HelperInstanceResolver typedHelperResolver() {
    return helperClass -> {
      // Force a single CronParseHelper so the reflective fallback does not allocate
      // one per call — the helper is stateless, but this matches the resolver style.
      if (helperClass == CronParseHelper.class) {
        return new CronParseHelper();
      }
      // CompensationTrackerHelper needs a Cache<String, Marker> (see AdvancedDslExamplesTest).
      if (helperClass == CompensationTrackerHelper.class) {
        return new CompensationTrackerHelper(Caffeine.newBuilder()
                .expireAfterWrite(cbs.nova.starter.core.StarterConstants.COMPENSATION_TRACKER_TTL)
                .maximumSize(cbs.nova.starter.core.StarterConstants.COMPENSATION_TRACKER_MAX_SIZE)
                .build());
      }
      // UnreliableApiHelper needs a Cache<String, HttpOutcome> with the project's TTL bounds.
      if (helperClass == cbs.nova.starter.helper.UnreliableApiHelper.class) {
        return new cbs.nova.starter.helper.UnreliableApiHelper(Caffeine.newBuilder()
                .expireAfterWrite(cbs.nova.starter.core.StarterConstants.UNRELIABLE_API_TTL)
                .maximumSize(cbs.nova.starter.core.StarterConstants.UNRELIABLE_API_MAX_SIZE)
                .build());
      }
      try {
        return (cbs.nova.dsl.Executable<?, ?>) helperClass.getDeclaredConstructor().newInstance();
      } catch (ReflectiveOperationException e) {
        throw new IllegalStateException("Cannot instantiate helper " + helperClass.getName(), e);
      }
    };
  }
}
