package cbs.nova.dsl.config;

import cbs.nova.dsl.ExecutionTraceCollector;
import cbs.nova.dsl.HelperRunner;
import cbs.nova.dsl.process.ProcessRunner;
import cbs.nova.dsl.runner.DefaultHelperRunner;
import cbs.nova.dsl.runner.DefaultProcessRunner;
import cbs.nova.dsl.runner.DefaultTransactionRunner;
import cbs.nova.dsl.transaction.TransactionRunner;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

@RequiredArgsConstructor
public final class DslRuntimeConfig {

  public @NonNull ExecutionTraceCollector executionTraceCollector() {
    return new ExecutionTraceCollector();
  }

  public @NonNull ContextFactory contextFactory() {
    return new ContextFactory();
  }

  public @NonNull RetryPolicyFactory retryPolicyFactory() {
    return new RetryPolicyFactory();
  }

  public @NonNull ProcessRunner processRunner(
          @NonNull ExecutionTraceCollector executionTraceCollector,
          @NonNull ContextFactory contextFactory) {
    return new DefaultProcessRunner(executionTraceCollector, contextFactory);
  }

  public @NonNull TransactionRunner transactionRunner(
          @NonNull ExecutionTraceCollector executionTraceCollector,
          @NonNull ContextFactory contextFactory) {
    return new DefaultTransactionRunner(executionTraceCollector, contextFactory);
  }

  public @NonNull HelperRunner helperRunner(
          @NonNull ExecutionTraceCollector executionTraceCollector,
          @NonNull ContextFactory contextFactory) {
    return new DefaultHelperRunner(executionTraceCollector, contextFactory);
  }
}
