package cbs.nova.starter.config;

import cbs.nova.starter.annotation.HelperBean;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.HttpCallProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.helper.CompensationTrackerHelper;
import cbs.nova.starter.helper.HttpCallHelper;
import cbs.nova.starter.helper.UnreliableApiHelper;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.http.HttpClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(SpringHelperBeanDefinitionRegistrar.class)
@EnableConfigurationProperties(HttpCallProperties.class)
public class SpringHelperConfiguration {

  @Bean
  public HelperBeanRegistryInitializer helperBeanRegistryInitializer() {
    return new HelperBeanRegistryInitializer();
  }

  @HelperBean("httpCall")
  public HttpCallHelper httpCallHelper(HttpClient httpClient,
          CbsNovaLoggingProperties loggingProperties,
          HttpCallProperties httpCallProperties) {
    return new HttpCallHelper(httpClient, loggingProperties, httpCallProperties);
  }

  @Bean
  public UnreliableApiHelper unreliableApiHelper() {
    return new UnreliableApiHelper(Caffeine.newBuilder()
            .expireAfterWrite(StarterConstants.UNRELIABLE_API_TTL)
            .maximumSize(StarterConstants.UNRELIABLE_API_MAX_SIZE)
            .build());
  }

  @Bean
  public CompensationTrackerHelper compensationTrackerHelper() {
    return new CompensationTrackerHelper(Caffeine.newBuilder()
            .expireAfterWrite(StarterConstants.COMPENSATION_TRACKER_TTL)
            .maximumSize(StarterConstants.COMPENSATION_TRACKER_MAX_SIZE)
            .build());
  }

}
