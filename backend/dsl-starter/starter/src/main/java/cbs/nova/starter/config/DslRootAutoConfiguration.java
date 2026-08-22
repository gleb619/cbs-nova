package cbs.nova.starter.config;

import cbs.nova.starter.preview.MessagingCallCaptureAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Import;

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
    DslExecutionsRouterConfiguration.class,
    DslIntrospectionRouterConfiguration.class,
    DslErrorHandlingAutoConfiguration.class,
    SpringHelperAutoConfiguration.class
})
public class DslRootAutoConfiguration {
}
