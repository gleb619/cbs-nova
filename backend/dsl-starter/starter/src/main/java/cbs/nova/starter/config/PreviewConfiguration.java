package cbs.nova.starter.config;

import cbs.nova.dsl.transaction.TransactionInvoker;
import cbs.nova.starter.core.recorder.ExternalCallRecorder;
import cbs.nova.starter.preview.TemporalActivityCallCaptureInterceptor;
import cbs.nova.starter.services.TemporalTransactionInvoker;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.context.annotation.Bean;

@Configuration
public class PreviewConfiguration {

  @Bean
  TransactionInvoker transactionInvoker(
          TemporalTransactionInvoker temporalTransactionInvoker,
          ExternalCallRecorder externalCallRecorder) {
    return new TemporalActivityCallCaptureInterceptor(temporalTransactionInvoker,
            externalCallRecorder);
  }
}
