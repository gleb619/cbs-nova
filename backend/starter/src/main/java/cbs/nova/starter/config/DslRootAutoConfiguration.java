package cbs.nova.starter.config;

import cbs.nova.starter.preview.MessagingCallCaptureAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * Single root autoconfiguration that aggregates every cbs-nova starter autoconfig. Only this class
 * is listed in
 * {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}; the
 * rest are pulled in through {@link Import} so the published artifact advertises one entry point.
 *
 * <p>
 * Import order is significant for
 * {@link org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean} resolution:
 * {@link TemporalConfiguration} precedes {@link DslAutoConfiguration} so that the Temporal-backed
 * {@code DslRunRepository} (and other infra beans) win over the fallbacks declared in the DSL core
 * config. {@link DryRunLoggingAutoConfiguration} precedes {@link TemporalConfiguration} because the
 * latter depends on the dry-run logging context propagator bean.
 * {@link DslRunRepositoryConfiguration} precedes {@link TemporalConfiguration} so the JDBC-backed
 * {@code DslRunRepository} is registered before the in-memory fallback.
 */
@AutoConfiguration
@Import({
    DryRunLoggingAutoConfiguration.class,
    DslRunRepositoryConfiguration.class,
    TemporalConfiguration.class,
    DslAutoConfiguration.class,
    DslWorkerConfiguration.class,
    DataSourceCallAutoConfiguration.class,
    FeignCallAutoConfiguration.class,
    PreviewAutoConfiguration.class,
    PreviewCacheAutoConfiguration.class,
    MessagingCallCaptureAutoConfiguration.class,
    PreviewMetricsAutoConfiguration.class,
    DslReloadRouterConfiguration.class,
    DslErrorHandlingAutoConfiguration.class
})
public class DslRootAutoConfiguration {
}
