package cbs.nova.starter.controller;

import cbs.nova.starter.model.ErrorResponse;
import cbs.nova.starter.model.ScheduleModels.CreateScheduleRequest;
import cbs.nova.starter.model.ScheduleModels.ScheduleSummary;
import cbs.nova.starter.service.DslScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.core.JacksonException;
import io.temporal.client.schedules.ScheduleClient;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;

import java.util.List;
import java.util.Map;

/**
 * Functional handler for DSL schedule CRUD. Registered as a {@code RouterFunction} bean by
 * {@link cbs.nova.starter.config.DslScheduleRouterConfiguration}.
 */
@Slf4j
@Component
@ConditionalOnBean(ScheduleClient.class)
@RequiredArgsConstructor
public class DslScheduleHandler {

  private final DslScheduleService service;
  private final ObjectMapper objectMapper;

  public ServerResponse create(ServerRequest request) throws IOException {
    CreateScheduleRequest body = parse(request);
    if (body == null) {
      return badRequest("Request body is required");
    }
    var response = service.create(body);
    return ServerResponse.status(HttpStatus.CREATED)
            .contentType(MediaType.APPLICATION_JSON)
            .body(response);
  }

  public ServerResponse list(ServerRequest request) {
    List<ScheduleSummary> schedules = service.list();
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(schedules);
  }

  public ServerResponse delete(ServerRequest request) {
    String definition = request.pathVariable("definition");
    service.delete(definition);
    return ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("deleted", true));
  }

  private CreateScheduleRequest parse(ServerRequest request) throws IOException {
    try {
      String body = request.body(String.class);
      if (body == null || body.isBlank()) {
        return null;
      }
      return objectMapper.readValue(body, CreateScheduleRequest.class);
    } catch (JacksonException e) {
      log.warn("[DSL schedules] failed to parse request body: {}", e.getMessage());
      return null;
    } catch (Exception e) {
      throw new IOException("Failed to read request body", e);
    }
  }

  private static ServerResponse badRequest(String message) {
    return ServerResponse.status(HttpStatus.BAD_REQUEST)
            .body(new ErrorResponse("BAD_REQUEST", message, null, null, null));
  }
}
