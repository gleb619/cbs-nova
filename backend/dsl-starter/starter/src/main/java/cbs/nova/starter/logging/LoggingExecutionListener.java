package cbs.nova.starter.logging;

import cbs.nova.dsl.ExecutionListener;
import cbs.nova.dsl.transaction.TransactionExecution;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties.Level;
import cbs.nova.starter.web.RequestIdFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.MDC;

import java.util.function.Supplier;

@Slf4j
@RequiredArgsConstructor
public final class LoggingExecutionListener implements ExecutionListener {

  private final CbsNovaLoggingProperties properties;

  @Override
  public void onProcessStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
    runWithMdc(runId, () -> log(Level.INFO, "DSL process start: {}", name, null));
  }

  @Override
  public void onProcessEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    runWithMdc(runId, () -> log(success ? Level.INFO : Level.ERROR,
            "DSL process end: {} success={}", name, success));
  }

  @Override
  public void onTransactionStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
    runWithMdc(runId, () -> log(Level.INFO, "DSL transaction start: {}", name, null));
  }

  @Override
  public void onTransactionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    runWithMdc(runId, () -> log(success ? Level.INFO : Level.ERROR,
            "DSL transaction end: {} success={}", name, success));
  }

  @Override
  public void onTransactionSuccess(@NonNull TransactionExecution execution) {
    runWithMdc(execution.runId(), () -> log(Level.INFO,
            "DSL transaction persisted: {}", execution.transactionName(), null));
  }

  @Override
  public void onTransactionFailure(@NonNull String runId, @NonNull String transactionName,
          @NonNull Throwable cause) {
    runWithMdc(runId, () -> log(Level.WARN,
            "DSL transaction failure: {} - {}", transactionName, cause.getMessage()));
  }

  @Override
  public void onHelperStart(@NonNull String runId, @NonNull String name, @Nullable Object input) {
    runWithMdc(runId, () -> log(Level.DEBUG, "DSL helper start: {}", name, null));
  }

  @Override
  public void onHelperEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    runWithMdc(runId, () -> log(success ? Level.DEBUG : Level.WARN,
            "DSL helper end: {} success={}", name, success));
  }

  @Override
  public void onFunctionStart(@NonNull String runId, @NonNull String name,
          @Nullable Object input) {
    runWithMdc(runId, () -> log(Level.DEBUG, "DSL function start: {}", name, null));
  }

  @Override
  public void onFunctionEnd(@NonNull String runId, @NonNull String name,
          @Nullable Object output, boolean success) {
    runWithMdc(runId, () -> log(success ? Level.DEBUG : Level.WARN,
            "DSL function end: {} success={}", name, success));
  }

  private void runWithMdc(@NonNull String runId, @NonNull Runnable action) {
    if (!properties.mdcEnabled()) {
      action.run();
      return;
    }
    MDC.put(RequestIdFilter.REQUEST_ID_MDC_KEY, runId);
    try {
      action.run();
    } finally {
      MDC.remove(RequestIdFilter.REQUEST_ID_MDC_KEY);
    }
  }

  private void log(@NonNull Level level, @NonNull String message, Object arg1, Object arg2) {
    if (level.ordinal() < properties.lifecycle().ordinal()) {
      return;
    }
    switch (level) {
      case DEBUG -> log.debug(message, arg1, arg2);
      case WARN -> log.warn(message, arg1, arg2);
      case ERROR -> log.error(message, arg1, arg2);
      default -> log.info(message, arg1, arg2);
    }
  }
}
