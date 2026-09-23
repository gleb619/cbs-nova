package cbs.nova.starter.controller;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.converter.RequestQueryConverter;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructSchemaMode;
import cbs.nova.starter.model.DslIntrospectionModels.DefinitionMetaDto;
import cbs.nova.starter.model.DslIntrospectionModels.HelperSearchResult;
import cbs.nova.starter.model.DslIntrospectionModels.ProcessDiagramDto;
import cbs.nova.starter.model.DslIntrospectionModels.WorkingSetResponse;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.reporting.HierarchyDiagramRenderer;
import cbs.nova.starter.service.DslIntrospectionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@RequiredArgsConstructor
public class DslIntrospectionHandler {

  private final DslIntrospectionService service;
  private final HierarchyDiagramRenderer diagramRenderer;
  private final RequestQueryConverter queryConverter;

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
    var q = queryConverter.toIntrospectionSearchQuery(request);
    List<HelperSearchResult> results = service.searchObjects(q.name(), q.type(), q.description());
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

  public ServerResponse constructSchema(ServerRequest request) {
    String name = request.pathVariable("name");
    ConstructSchemaMode mode = ConstructSchemaMode.from(request.param("mode").orElse(null));
    return service.constructSchema(name, mode)
            .map(s -> ServerResponse.ok().body(s))
            .orElse(ServerResponse.notFound().build());
  }

  public ServerResponse objectStructure(ServerRequest request) {
    String name = request.pathVariable("name");
    return service.objectStructure(name)
            .map(s -> ServerResponse.ok().body(s))
            .orElse(ServerResponse.notFound().build());
  }

  public ServerResponse workingSet(ServerRequest request) {
    var q = queryConverter.toWorkingSetQuery(request);
    WorkingSetResponse response = service.workingSet(q);
    return ServerResponse.ok().body(response);
  }
  public ServerResponse definitions(ServerRequest request) {
    int limit = Pagination.intParam(request, "limit", StarterConstants.DEFAULT_LIMIT);
    int offset = Pagination.intParam(request, "offset", StarterConstants.DEFAULT_OFFSET);
    int pageSize = Pagination.clampLimit(limit);
    int skip = Pagination.clampOffset(offset);

    List<DefinitionMetaDto> aggregate = service.definitions();
    long total = aggregate.size();
    List<DefinitionMetaDto> paged = aggregate.stream()
            .skip(skip)
            .limit(pageSize)
            .toList();
    return ServerResponse.ok().body(new PageResponse<>(paged, total, skip, pageSize));
  }

}
