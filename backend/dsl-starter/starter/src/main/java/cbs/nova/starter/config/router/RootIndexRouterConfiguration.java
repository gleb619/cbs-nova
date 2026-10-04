package cbs.nova.starter.config.router;

import cbs.nova.starter.config.properties.CbsNovaIndexProperties;
import cbs.nova.starter.controller.RootIndexHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * Registers the optional root index page when {@code cbs.nova.index-page.enabled=true}.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "cbs.nova.index-page", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(CbsNovaIndexProperties.class)
public class RootIndexRouterConfiguration {

  @Bean
  RootIndexHandler rootIndexHandler(CbsNovaIndexProperties properties,
          Environment environment,
          @Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping,
          @Value("classpath:index.html") Resource indexHtml) {
    return new RootIndexHandler(properties, environment, handlerMapping, indexHtml);
  }

  @Bean
  public RouterFunction<ServerResponse> rootIndexRouter(RootIndexHandler handler) {
    return RouterFunctions.route()
            .GET("/", handler::index)
            .build();
  }
}
