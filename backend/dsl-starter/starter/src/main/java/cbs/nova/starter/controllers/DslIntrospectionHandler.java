package cbs.nova.starter.controllers;

import cbs.nova.starter.services.introspection.DslIntrospectionService;
import cbs.nova.starter.services.introspection.model.DefinitionMetaDto;
import cbs.nova.starter.services.introspection.model.HelperSearchResult;
import cbs.nova.starter.services.introspection.model.NamesResponse;
import cbs.nova.starter.services.introspection.model.ProcessDetail;
import cbs.nova.starter.services.introspection.model.TransactionDetail;
import lombok.RequiredArgsConstructor;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.List;

@RequiredArgsConstructor
// TODO: Add mapstrcut mapper, that map `request.param` to a record
public class DslIntrospectionHandler {

  private final DslIntrospectionService service;

  public ServerResponse processes(ServerRequest request) {
    return ServerResponse.ok().body(service.processes());
  }

  public ServerResponse processDetail(ServerRequest request) {
    String name = request.pathVariable("name");
    return service.processDetail(name)
            .map(p -> ServerResponse.ok().body(p))
            .orElse(ServerResponse.notFound().build());
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

  public ServerResponse definitions(ServerRequest request) {
    List<DefinitionMetaDto> aggregate = service.definitions();
    return ServerResponse.ok().body(aggregate);
  }
}
