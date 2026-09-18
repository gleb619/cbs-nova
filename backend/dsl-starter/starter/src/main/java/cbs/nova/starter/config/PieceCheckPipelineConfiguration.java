package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.service.DomainEventPublisher;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.PieceCheckBlockRegistry;
import cbs.nova.starter.service.PieceCheckPipeline;
import cbs.nova.starter.service.PieceManifestService;
import cbs.nova.starter.service.check.AuditWriteHook;
import cbs.nova.starter.service.check.InvariantAssertHook;
import cbs.nova.starter.service.check.NotifyHook;
import cbs.nova.starter.service.check.PostCheckHook;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Registers the T550 post-check hook pipeline as a first-class auto-configuration.
 *
 * <p>
 * Follows the T541/T548 lesson (same pattern as {@code PieceGuardFilterConfiguration}): activated
 * only when {@code PieceManifestService} is present ({@code cbs.dsl.manifest.enabled} with a
 * loadable manifest subsystem), so the pipeline vanishes entirely when the manifest subsystem is
 * off. {@code after} pins the evaluation order behind {@code DslManifestConfiguration}; the guard
 * filter consumes the pipeline and registry lazily via {@link ObjectProvider}, so no hard
 * bean-ordering coupling against {@code PieceGuardFilterConfiguration} is needed.
 *
 * <p>
 * Hooks run on a dedicated named-thread pool ({@code cbs-piece-check-N}) — never the request thread
 * — sized via {@code cbs.dsl.manifest.post-check.executor-pool-size}.
 */
@Slf4j
@AutoConfiguration(after = DslManifestConfiguration.class)
@ConditionalOnBean(PieceManifestService.class)
public class PieceCheckPipelineConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public PieceCheckBlockRegistry pieceCheckBlockRegistry(CbsDslManifestProperties properties) {
    return new PieceCheckBlockRegistry(properties, System::currentTimeMillis);
  }

  @Bean(name = "cbsNovaPieceCheckExecutor", destroyMethod = "shutdownNow")
  @ConditionalOnMissingBean(name = "cbsNovaPieceCheckExecutor")
  ExecutorService cbsNovaPieceCheckExecutor(CbsDslManifestProperties properties) {
    // Same idiom as cbsNovaPreviewDispatchExecutor: fixed named daemon pool.
    int poolSize = properties.postCheck().executorPoolSize();
    AtomicInteger counter = new AtomicInteger(1);
    ThreadFactory threadFactory = r -> {
      Thread thread = new Thread(r, "cbs-piece-check-" + counter.getAndIncrement());
      thread.setDaemon(true);
      return thread;
    };
    return Executors.newFixedThreadPool(poolSize, threadFactory);
  }

  @Bean
  @ConditionalOnMissingBean(AuditWriteHook.class)
  public AuditWriteHook auditWriteHook(ObjectProvider<DslAuditService> auditServiceProvider) {
    return new AuditWriteHook(auditServiceProvider);
  }

  @Bean
  @ConditionalOnMissingBean(InvariantAssertHook.class)
  public InvariantAssertHook invariantAssertHook() {
    return new InvariantAssertHook();
  }

  @Bean
  @ConditionalOnMissingBean(NotifyHook.class)
  public NotifyHook notifyHook(ObjectProvider<DomainEventPublisher> eventPublisherProvider) {
    return new NotifyHook(eventPublisherProvider);
  }

  @Bean
  @ConditionalOnMissingBean
  public PieceCheckPipeline pieceCheckPipeline(
          CbsDslManifestProperties properties,
          ExecutorService cbsNovaPieceCheckExecutor,
          List<PostCheckHook> hooks,
          PieceCheckBlockRegistry blockRegistry,
          ObjectProvider<DslAuditService> auditServiceProvider,
          ObjectProvider<MeterRegistry> meterRegistryProvider) {
    log.info("PieceCheckPipeline active — post-check hooks ({}) run async on {} thread(s)",
            hooks.size(), properties.postCheck().executorPoolSize());
    return new PieceCheckPipeline(cbsNovaPieceCheckExecutor, hooks, blockRegistry,
            auditServiceProvider, meterRegistryProvider.getIfAvailable());
  }
}
