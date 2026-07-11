package cbs.nova.starter.controllers;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.starter.models.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/dsl")
@Tag(name = "DSL Admin", description = "Administrative operations for DSL runtime")
public class DslReloadResource {

  @Value("${dsl.source-dir:}")
  private String sourceDirProperty;

  public DslReloadResource() {
  }

  @PostMapping("/reload")
  @Operation(summary = "Reload DSL definitions from configured source directory")
  public ResponseEntity<?> reload() {
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return ResponseEntity.status(409)
              .body(
                      new ErrorResponse(
                              "NOT_CONFIGURED", "dsl.source-dir is not configured", null, null,
                              null));
    }
    var dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      return ResponseEntity.status(409)
              .body(
                      new ErrorResponse(
                              "NOT_FOUND",
                              "Source directory does not exist: " + dir,
                              null,
                              null,
                              null));
    }
    GlobalManager.getInstance().resetForTests();
    try {
      new DefinitionLoader().load(dir, GlobalManager.getInstance());
      return ResponseEntity.noContent().build();
    } catch (Exception e) {
      return ResponseEntity.status(500)
              .body(
                      new ErrorResponse("RELOAD_FAILED", e.getMessage(), null, null, null));
    }
  }
}
