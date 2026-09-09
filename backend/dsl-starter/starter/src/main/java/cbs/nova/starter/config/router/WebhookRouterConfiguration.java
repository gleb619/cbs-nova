package cbs.nova.starter.config.router;

import cbs.nova.starter.controller.WebhookHandler;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.webhook.WebhookDeliveryInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
@ConditionalOnProperty(prefix = "cbs.webhook", name = "enabled", havingValue = "true")
public class WebhookRouterConfiguration {

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/dsl/webhooks/deliveries", beanClass = WebhookHandler.class, beanMethod = "list", method = RequestMethod.GET, operation = @Operation(operationId = "listWebhookDeliveryRows", summary = "List persisted webhook delivery outcomes", tags = {
          "Webhooks"}, parameters = {
              @Parameter(name = "subscriptionId", in = ParameterIn.QUERY, description = "Optional exact subscription id filter (definition pattern)"),
              @Parameter(name = "limit", in = ParameterIn.QUERY, description = "Maximum number of deliveries to return"),
              @Parameter(name = "offset", in = ParameterIn.QUERY, description = "Number of matching deliveries to skip before returning results")
          }, responses = @ApiResponse(responseCode = "200", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class))))),
      @RouterOperation(path = "/api/webhooks/deliveries", beanClass = WebhookHandler.class, beanMethod = "deliveries", method = RequestMethod.GET, operation = @Operation(operationId = "listWebhookDeliveries", summary = "List last webhook delivery outcomes", tags = {
          "Webhooks"}, responses = @ApiResponse(responseCode = "200", description = "Last delivery outcome per configured subscription", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = WebhookDeliveryInfo.class))))))
  })
  public RouterFunction<ServerResponse> webhookRouter(WebhookHandler handler) {
    return RouterFunctions.route()
            .GET("/api/dsl/webhooks/deliveries", handler::list)
            .GET("/api/webhooks/deliveries", handler::deliveries)
            .build();
  }
}
