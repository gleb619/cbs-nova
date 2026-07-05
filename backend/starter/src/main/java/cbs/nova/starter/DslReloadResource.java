package cbs.nova.starter;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.GlobalManager;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;

@RestController
@RequestMapping("/api/dsl")
public class DslReloadResource {

  @Value("${dsl.source-dir:}")
  private String sourceDirProperty;

  private final GlobalManager globalManager;

  public DslReloadResource(@NonNull GlobalManager globalManager) {
    this.globalManager = globalManager;
  }

  @PostMapping("/reload")
  public ResponseEntity<?> reload() {
    if (sourceDirProperty == null || sourceDirProperty.isBlank()) {
      return ResponseEntity.status(409)
              .body(new ErrorResponse("NOT_CONFIGURED", "dsl.source-dir is not configured", null));
    }
    var dir = Path.of(sourceDirProperty);
    if (!Files.isDirectory(dir)) {
      return ResponseEntity.status(409)
              .body(new ErrorResponse("NOT_FOUND", "Source directory does not exist: " + dir,
                      null));
    }
    DefinitionLoader.load(dir, globalManager);
    return ResponseEntity.noContent().build();
  }
}
