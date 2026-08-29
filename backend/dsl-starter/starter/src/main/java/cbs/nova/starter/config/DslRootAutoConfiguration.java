package cbs.nova.starter.config;

import cbs.nova.starter.config.properties.DslProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@AutoConfigureAfter({DslRunRepositoryConfiguration.class, DataSourceAutoConfiguration.class})
@Import({
    RequestIdFilterConfiguration.class,
    LoggingConfiguration.class,
    DryRunLoggingConfiguration.class,
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
    DslRunRetentionConfiguration.class,
    DslErrorHandlingConfiguration.class,
    SpringHelperConfiguration.class,
    ApiKeyAuthFilterConfiguration.class,

})
@EnableConfigurationProperties({DslProperties.class, CbsNovaCacheProperties.class})
public class DslRootAutoConfiguration {
}
