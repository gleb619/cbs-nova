package cbs.nova.starter.controller;

import cbs.nova.dsl.model.ErrorResponse;
import cbs.nova.starter.exception.ChangeRequestException;
import cbs.nova.starter.service.ChangeRequestService;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.entity.ChangeRequestEntity;
import cbs.nova.starter.entity.ChangeRequestEntity.Status;
import cbs.nova.starter.security.Role;
import cbs.nova.starter.security.RoleResolver;
import cbs.nova.starter.service.DslAuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.ServletException;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Functional handler for the T568 change-request approval gate: submit (snapshots the current
 * draft), list (optionally filtered), approve (publishes the snapshot through
 * {@link DslDraftHandler#publishPayload}) and reject. Thin HTTP shell around
 * {@link ChangeRequestService}; the caller's {@link Role} is resolved with the same shared
 * {@link RoleResolver} the RBAC filter and piece guard use.
 */
@Tag(name = "DSL Admin", description = "Change-request approval gate (T568)")
@RequiredArgsConstructor
public class ChangeRequestHandler {

  private final ChangeRequestService changeRequestService;
  private final RoleResolver roleResolver;
  private final ObjectMapper objectMapper;

  @Operation(summary = "Submit a change request snapshotting the current draft for approval")
  @ApiResponse(responseCode = "201", description = "Change request created")
  @ApiResponse(responseCode = "404", description = "No draft exists for the definition", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  public ServerResponse submit(ServerRequest request) {
    String name = request.pathVariable("name");
    try {
      ChangeRequestEntity created = changeRequestService.submit(name,
              DslAuditService.currentActor(), request);
      return ServerResponse.status(HttpStatus.CREATED)
              .contentType(MediaType.APPLICATION_JSON)
              .body(created);
    } catch (ChangeRequestException e) {
      return error(e, name);
    }
  }

  @Operation(summary = "List change requests, optionally filtered by definitionName and status")
  @ApiResponse(responseCode = "200", description = "Matching change requests, newest first")
  public ServerResponse list(ServerRequest request) {
    String definitionName = request.param("definitionName")
            .filter(s -> !s.isBlank())
            .orElse(null);
    Status status = null;
    var statusParam = request.param("status").filter(s -> !s.isBlank());
    if (statusParam.isPresent()) {
      try {
        status = Status.valueOf(statusParam.get().trim().toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException ex) {
        return ServerResponse.status(HttpStatus.BAD_REQUEST)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new ErrorResponse("INVALID_REQUEST",
                        "Invalid value for query parameter 'status': '" + statusParam.get()
                                + "' (expected one of PENDING, APPROVED, REJECTED, SUPERSEDED)",
                        null, null, null, null, null, null, null));
      }
    }
    List<ChangeRequestEntity> items = changeRequestService.list(definitionName, status);
    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(items);
  }

  @Operation(summary = "Approve a pending change request and publish its snapshot")
  @ApiResponse(responseCode = "200", description = "Publish result of the approved snapshot")
  @ApiResponse(responseCode = "403", description = "Caller rank too low or self-approval", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "404", description = "No change request with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "409", description = "Change request is not pending", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  public ServerResponse approve(ServerRequest request) throws ServletException, IOException {
    long id = Long.parseLong(request.pathVariable("id"));
    try {
      return changeRequestService.approve(id, DslAuditService.currentActor(), role(request),
              commentOf(request), request);
    } catch (ChangeRequestException e) {
      return error(e, null);
    }
  }

  @Operation(summary = "Reject a pending change request")
  @ApiResponse(responseCode = "200", description = "The rejected change request")
  @ApiResponse(responseCode = "403", description = "Caller rank too low or self-rejection", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "404", description = "No change request with the given id", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  @ApiResponse(responseCode = "409", description = "Change request is not pending", content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
  public ServerResponse reject(ServerRequest request) throws ServletException, IOException {
    long id = Long.parseLong(request.pathVariable("id"));
    try {
      ChangeRequestEntity rejected = changeRequestService.reject(id,
              DslAuditService.currentActor(), role(request), commentOf(request), request);
      return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON).body(rejected);
    } catch (ChangeRequestException e) {
      return error(e, null);
    }
  }

  private Role role(ServerRequest request) {
    return roleResolver.resolve(request.servletRequest());
  }

  private @Nullable String commentOf(ServerRequest request) throws ServletException, IOException {
    try {
      String raw = request.body(String.class);
      if (raw == null || raw.isBlank()) {
        return null;
      }
      return objectMapper.readValue(raw, CommentRequest.class).comment();
    } catch (JacksonException | IllegalStateException ex) {
      throw new IllegalArgumentException("malformed JSON body: " + ex.getMessage(), ex);
    }
  }

  private static ServerResponse error(ChangeRequestException e, @Nullable String definitionName) {
    HttpStatus status = switch (e.code()) {
      case "NOT_FOUND" -> HttpStatus.NOT_FOUND;
      case "FORBIDDEN" -> HttpStatus.FORBIDDEN;
      default -> HttpStatus.CONFLICT;
    };
    return ServerResponse.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(new ErrorResponse(e.code().equals("FORBIDDEN")
                    ? StarterConstants.FORBIDDEN_CODE
                    : e.code(), e.getMessage(), definitionName, null, null, null, null, null,
                    null));
  }

  record CommentRequest(@Nullable String comment) {
  }
}
