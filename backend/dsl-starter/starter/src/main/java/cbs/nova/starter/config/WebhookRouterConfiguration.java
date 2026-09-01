package cbs.nova.starter.config;

import cbs.nova.starter.controller.WebhookHandler;
import cbs.nova.starter.webhook.WebhookDeliveryInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.RouterOperation;
import org.springdoc.core.annotations.RouterOperations;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

@Configuration
public class WebhookRouterConfiguration {

  @Bean
  @RouterOperations({
      @RouterOperation(path = "/api/webhooks/deliveries", beanClass = WebhookHandler.class, beanMethod = "deliveries", method = RequestMethod.GET, operation = @Operation(operationId = "listWebhookDeliveries", summary = "List last webhook delivery outcomes", tags = {
          "Webhooks"}, responses = @ApiResponse(responseCode = "200", description = "Last delivery outcome per configured subscription", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = WebhookDeliveryInfo.class))))))
  })
  public RouterFunction<ServerResponse> webhookRouter(WebhookHandler handler) {
    return RouterFunctions.route()
            .GET("/api/webhooks/deliveries", handler::deliveries)
            .build();
  }
}
