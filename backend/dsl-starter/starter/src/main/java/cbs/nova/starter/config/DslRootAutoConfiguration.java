package cbs.nova.starter.config;

import cbs.nova.starter.preview.MessagingCallCaptureAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    RequestIdFilterConfiguration.class,
    LoggingAutoConfiguration.class,
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
    DslRouterConfiguration.class,
    DslErrorHandlingAutoConfiguration.class,
    SpringHelperAutoConfiguration.class
})
public class DslRootAutoConfiguration {
}
