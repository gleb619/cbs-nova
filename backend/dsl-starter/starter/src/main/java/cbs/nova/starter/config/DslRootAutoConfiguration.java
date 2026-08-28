package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.DslProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@Import({
    RequestIdFilterConfiguration.class,
    LoggingConfiguration.class,
    DryRunLoggingConfiguration.class,
    DslRunRepositoryConfiguration.class,
    DslConfiguration.class,
    DslWorkerConfiguration.class,
    TemporalConfiguration.class,
    DataSourceCallConfiguration.class,
    FeignCallConfiguration.class,
    PreviewConfiguration.class,
    PreviewCacheConfiguration.class,
    MessagingCallCaptureConfiguration.class,
    PreviewMetricsConfiguration.class,
    DslRouterConfiguration.class,
    DslErrorHandlingConfiguration.class,
    SpringHelperConfiguration.class,
    ApiKeyAuthFilterConfiguration.class,

})
@EnableConfigurationProperties({DslProperties.class, CbsNovaCacheProperties.class})
public class DslRootAutoConfiguration {
}
