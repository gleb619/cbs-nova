package cbs.nova.starter.controller;

import cbs.nova.starter.model.DslEventDto;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.persistence.DslEventRepository;
import cbs.nova.starter.persistence.DslEventSearchResult;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
public class DslEventHandler {

  private final DslEventRepository repository;
  private final ObjectMapper objectMapper;

  public ServerResponse list(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", Pagination.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", Pagination.DEFAULT_OFFSET);
    int pageSize = Pagination.clampLimit(limit);
    int skip = Pagination.clampOffset(offset);

    String type = request.param("type").filter(s -> !s.isBlank()).orElse(null);
    String aggregateType = request.param("aggregateType").filter(s -> !s.isBlank()).orElse(null);
    String aggregateId = request.param("aggregateId").filter(s -> !s.isBlank()).orElse(null);
    String correlationId = request.param("correlationId").filter(s -> !s.isBlank()).orElse(null);
    Instant since = parseInstant(request, "since");

    DslEventSearchResult result = repository.search(type, aggregateType, aggregateId,
            correlationId, since, skip, pageSize);
    List<DslEventDto> items = result.items().stream()
            .map(entity -> DslEventDto.from(entity, objectMapper))
            .toList();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(new PageResponse<>(items, result.total(), skip, pageSize));
  }

  private static Instant parseInstant(ServerRequest request, String name) {
    var raw = request.param(name).filter(s -> !s.isBlank()).orElse(null);
    if (raw == null) {
      return null;
    }
    try {
      return Instant.parse(raw.trim());
    } catch (DateTimeParseException e) {
      throw new IllegalArgumentException(
              "Invalid value for query parameter '" + name + "': '" + raw
                      + "' (expected ISO-8601 instant)");
    }
  }
}
