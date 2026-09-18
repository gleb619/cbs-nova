package cbs.nova.starter.config;

import cbs.nova.starter.core.pipe.ExplainBudget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.CompletableFuture;

/**
 * Async startup warmup for the CL100K_BASE tokenizer used by {@link ExplainBudget}.
 * <p>
 * The jtokkit encoding registry initialization costs ~430 ms on a cold JVM. This hook pays that
 * cost once, in the background, after all singletons are wired, so the first explain request does
 * not block on it.
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
public class ExplainBudgetStageWarmupConfiguration {

  @Bean
  SmartInitializingSingleton explainBudgetStageEncodingWarmup() {
    return () -> CompletableFuture.runAsync(() -> {
      long start = System.nanoTime();
      ExplainBudget.warmUpEncoding();
      long ms = (System.nanoTime() - start) / 1_000_000L;
      log.debug("CL100K_BASE encoding warmup completed in {} ms", ms);
    });
  }
}
