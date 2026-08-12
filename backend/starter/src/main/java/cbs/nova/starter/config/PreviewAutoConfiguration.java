package cbs.nova.starter.config;

import cbs.nova.dsl.TransactionInvoker;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.preview.TemporalActivityCallCaptureInterceptor;
import cbs.nova.starter.services.TemporalTransactionInvoker;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;

/**
 * Preview/explain autoconfiguration that wraps the raw {@link TemporalTransactionInvoker} with
 * call-capture interceptors so dry-run reports include activity calls.
 */
@AutoConfiguration
@AutoConfigureAfter(TemporalConfiguration.class)
public class PreviewAutoConfiguration {

  @Bean
  TransactionInvoker transactionInvoker(
          TemporalTransactionInvoker temporalTransactionInvoker,
          ExternalCallRecorder externalCallRecorder) {
    return new TemporalActivityCallCaptureInterceptor(temporalTransactionInvoker,
            externalCallRecorder);
  }
}
