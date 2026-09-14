package cbs.nova.starter.config;

import cbs.nova.starter.annotation.HelperBean;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties;
import cbs.nova.starter.config.properties.CbsNovaLoggingProperties.Level;
import cbs.nova.starter.config.properties.HttpCallProperties;
import cbs.nova.starter.config.properties.JsonPatchProperties;
import cbs.nova.starter.config.properties.JwtProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.helper.CompensationTrackerHelper;
import cbs.nova.starter.helper.HttpCallHelper;
import cbs.nova.starter.helper.JsonPatchHelper;
import cbs.nova.starter.helper.JwtHelper;
import cbs.nova.starter.helper.UnreliableApiHelper;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.net.http.HttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

@Configuration
@Import(SpringHelperBeanDefinitionRegistrar.class)
@EnableConfigurationProperties({HttpCallProperties.class, JsonPatchProperties.class,
    JwtProperties.class})
public class SpringHelperConfiguration {

  @Bean
  public HelperBeanRegistryInitializer helperBeanRegistryInitializer() {
    return new HelperBeanRegistryInitializer();
  }

  @HelperBean("jsonPatch")
  public JsonPatchHelper jsonPatchHelper(ObjectProvider<ObjectMapper> objectMapperProvider,
          JsonPatchProperties jsonPatchProperties) {
    return new JsonPatchHelper(objectMapperProvider.getIfAvailable(ObjectMapper::new),
            jsonPatchProperties);
  }

  @HelperBean("jwt")
  public JwtHelper jwtHelper(ObjectProvider<ObjectMapper> objectMapperProvider,
          JwtProperties jwtProperties) {
    return new JwtHelper(objectMapperProvider.getIfAvailable(ObjectMapper::new), jwtProperties);
  }

  @HelperBean("httpCall")
  public HttpCallHelper httpCallHelper(ObjectProvider<HttpClient> httpClientProvider,
          ObjectProvider<CbsNovaLoggingProperties> loggingPropertiesProvider,
          HttpCallProperties httpCallProperties) {
    HttpClient httpClient = httpClientProvider.getIfAvailable(HttpClient::newHttpClient);
    CbsNovaLoggingProperties loggingProperties = loggingPropertiesProvider.getIfAvailable(
            () -> new CbsNovaLoggingProperties(Level.INFO, Level.INFO, true));
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
