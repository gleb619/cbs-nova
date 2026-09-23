package cbs.nova.starter.controller;

import cbs.nova.starter.converter.RequestQueryConverter;
import cbs.nova.starter.model.DslIntrospectionModels.ConstructSchemaMode;
import cbs.nova.starter.model.DslIntrospectionModels.ObjectSearchResult;
import cbs.nova.starter.model.DslIntrospectionModels.WorkingSetResponse;
import cbs.nova.starter.model.PageResponse;
import cbs.nova.starter.service.DslIntrospectionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

@RequiredArgsConstructor
public class DslIntrospectionHandler {

  private final DslIntrospectionService service;
  private final RequestQueryConverter queryConverter;

  public ServerResponse searchObjects(ServerRequest request) {
    var q = queryConverter.toObjectSearchQuery(request);
    PageResponse<ObjectSearchResult> page = service.searchObjects(
            q.page(), q.size(), q.query(), q.mode(), q.type());
    return ServerResponse.ok().body(page);
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
    var q = queryConverter.toObjectSearchQuery(request);
    WorkingSetResponse response = service.workingSet(q);
    return ServerResponse.ok().body(response);
  }
}
