package cbs.nova.starter.controller;

import cbs.nova.starter.webhook.WebhookDeliveryInfo;
import cbs.nova.starter.webhook.WebhookDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
@Tag(name = "Webhooks", description = "Run-completion webhook diagnostics")
@RequiredArgsConstructor
public class WebhookHandler {

  private final WebhookDispatcher webhookDispatcher;

  @Operation(summary = "List last webhook delivery outcomes")
  @ApiResponse(responseCode = "200", description = "Last delivery outcome per configured subscription", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = WebhookDeliveryInfo.class))))
  public ServerResponse deliveries(ServerRequest request) {
    List<WebhookDeliveryInfo> infos = List.copyOf(webhookDispatcher.deliveryInfos());
    return ServerResponse.ok().body(infos);
  }
}
