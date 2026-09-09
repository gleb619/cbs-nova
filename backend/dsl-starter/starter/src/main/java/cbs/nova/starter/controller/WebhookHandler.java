package cbs.nova.starter.controller;

import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.model.WebhookDeliveryDto;
import cbs.nova.starter.webhook.WebhookDeliveryRecordRepository;
import cbs.nova.starter.webhook.WebhookDeliveryInfo;
import cbs.nova.starter.webhook.WebhookDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@Component
@Tag(name = "Webhooks", description = "Run-completion webhook diagnostics")
@ConditionalOnProperty(prefix = "cbs.webhook", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class WebhookHandler {

  private final Optional<WebhookDispatcher> webhookDispatcher;

  private final WebhookDeliveryRecordRepository deliveryRepository;

  @Operation(summary = "List last webhook delivery outcomes")
  @ApiResponse(responseCode = "200", description = "Last delivery outcome per configured subscription", content = @Content(mediaType = "application/json", array = @ArraySchema(schema = @Schema(implementation = WebhookDeliveryInfo.class))))
  public ServerResponse deliveries(ServerRequest request) {
    return webhookDispatcher.map(_ -> {
      List<WebhookDeliveryInfo> infos = List.copyOf(webhookDispatcher.get().deliveryInfos());
      return ServerResponse.ok().body(infos);
    }).orElseGet(() -> ServerResponse.badRequest().build());
  }

  @Operation(summary = "List persisted webhook delivery outcomes")
  @ApiResponse(responseCode = "200", description = "Paged append-only delivery log, newest first", content = @Content(mediaType = "application/json", schema = @Schema(implementation = PageResponse.class)))
  public ServerResponse list(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", Pagination.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", Pagination.DEFAULT_OFFSET);
    int pageSize = Pagination.clampLimit(limit);
    int skip = Pagination.clampOffset(offset);
    String subscriptionId = request.param("subscriptionId").filter(s -> !s.isBlank()).orElse(null);

    var result = deliveryRepository.search(subscriptionId, skip, pageSize);
    List<WebhookDeliveryDto> items = result.items().stream()
            .map(WebhookDeliveryDto::from)
            .toList();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new PageResponse<>(items, result.total(), skip, pageSize));
  }
}
