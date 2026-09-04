package cbs.nova.starter.controller;

import cbs.nova.starter.reporting.ExplainDiagramRenderer;
import cbs.nova.starter.service.DslIntrospectionService;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructBodyDto;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionMetaDto;
import cbs.nova.starter.model.DslIntrospectionModels.HelperSearchResult;
import cbs.nova.starter.model.DslIntrospectionModels.ProcessDiagramDto;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

@RequiredArgsConstructor
// TODO: Add mapstrcut mapper, that map `request.param` to a record
public class DslIntrospectionHandler {

  private final DslIntrospectionService service;
  private final ExplainDiagramRenderer diagramRenderer;

  public ServerResponse processes(ServerRequest request) {
    return ServerResponse.ok().body(service.processes());
  }

  public ServerResponse processDetail(ServerRequest request) {
    String name = request.pathVariable("name");
    return service.processDetail(name)
            .map(p -> ServerResponse.ok().body(p))
            .orElse(ServerResponse.notFound().build());
  }

  public ServerResponse processDiagram(ServerRequest request) {
    String name = request.pathVariable("name");
    String format = request.param("format").orElse("mermaid");
    String diagram = diagramRenderer.renderByName(name, format);
    if (diagram == null) {
      return ServerResponse.notFound().build();
    }
    return ServerResponse.ok().body(new ProcessDiagramDto(name, format, diagram));
  }

  public ServerResponse transactions(ServerRequest request) {
    return ServerResponse.ok().body(service.transactions());
  }

  public ServerResponse transactionDetail(ServerRequest request) {
    String name = request.pathVariable("name");
    return service.transactionDetail(name)
            .map(t -> ServerResponse.ok().body(t))
            .orElse(ServerResponse.notFound().build());
  }

  public ServerResponse searchObjects(ServerRequest request) {
    String name = request.param("name").orElse(null);
    String type = request.param("type").orElse(null);
    String description = request.param("description").orElse(null);
    List<HelperSearchResult> results = service.searchObjects(name, type, description);
    return ServerResponse.ok().body(results);
  }

  public ServerResponse helpers(ServerRequest request) {
    return ServerResponse.ok().body(service.helpers());
  }

  public ServerResponse constructBody(ServerRequest request) {
    String name = request.pathVariable("name");
    return service.constructBody(name)
            .map(b -> ServerResponse.ok().body(b))
            .orElse(ServerResponse.notFound().build());
  }

  public ServerResponse definitions(ServerRequest request) {
    List<DefinitionMetaDto> aggregate = service.definitions();
    return ServerResponse.ok().body(aggregate);
  }

  public ServerResponse updateDescription(ServerRequest request) {
    String name = request.pathVariable("name");
    DescriptionUpdate body;
    try {
      body = request.body(DescriptionUpdate.class);
    } catch (Exception e) {
      return ServerResponse.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST",
              "description is required", e.getMessage()));
    }
    if (body == null || body.description() == null) {
      return ServerResponse.status(HttpStatus.BAD_REQUEST).body(new ErrorResponse("INVALID_REQUEST",
              "description is required", name));
    }
    try {
      service.updateDescription(name, body.description());
      return ServerResponse.ok().build();
    } catch (IllegalArgumentException e) {
      return ServerResponse.status(HttpStatus.NOT_FOUND)
              .body(new ErrorResponse("NOT_FOUND", e.getMessage(), name));
    }
  }

  private record DescriptionUpdate(String description) {
  }

  private record ErrorResponse(String code, String message, Object details) {
  }
}
