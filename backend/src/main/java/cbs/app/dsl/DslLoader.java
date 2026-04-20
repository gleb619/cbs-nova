package cbs.app.dsl;

import cbs.dsl.runtime.DslRegistry;
import cbs.dsl.script.DslScopeExtractor;
import cbs.dsl.script.EvalResult;
import cbs.dsl.script.ScriptHost;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Slf4j
@Component
@RequiredArgsConstructor
public class DslLoader implements ApplicationListener<ApplicationReadyEvent> {

  private final ScriptHost scriptHost;
  private final DslRegistry dslRegistry;

  @Value("${cbs.dsl.scripts-dir:}")
  String scriptsDir;

  @Override
  public void onApplicationEvent(ApplicationReadyEvent event) {
    if (scriptsDir == null || scriptsDir.isBlank()) {
      log.warn("DSL scripts directory is not configured. Set 'cbs.dsl.scripts-dir' to load .kts "
          + "files on startup.");
      return;
    }

    Path dir = Path.of(scriptsDir);
    if (!Files.isDirectory(dir)) {
      log.warn("DSL scripts directory does not exist or is not a directory: {}", scriptsDir);
      return;
    }

    int[] fileCount = {0};
    int[] successCount = {0};
    int[] failCount = {0};

    try (Stream<Path> files = Files.list(dir)) {
      files.filter(p -> p.toString().endsWith(".kts")).forEach(p -> {
        fileCount[0]++;
        loadScript(p, dslRegistry, successCount, failCount);
      });
    } catch (IOException e) {
      log.warn("Failed to list DSL scripts directory: {}", scriptsDir, e);
      return;
    }

    log.info(
        "DSL scripts loaded: {} found, {} succeeded, {} failed",
        fileCount[0],
        successCount[0],
        failCount[0]);
  }

  private void loadScript(
      Path path, DslRegistry sharedRegistry, int[] successCount, int[] failCount) {
    String fileName = path.getFileName().toString();
    try {
      String content = Files.readString(path);
      EvalResult result = DslScopeExtractor.evalAndExtract(scriptHost, content, fileName);
      if (result instanceof EvalResult.Success success) {
        mergeRegistry(success.getRegistry(), sharedRegistry);
        successCount[0]++;
        log.info("Loaded DSL script: {}", fileName);
      } else {
        failCount[0]++;
        log.warn(
            "Failed to load DSL script '{}': {}",
            fileName,
            ((EvalResult.Failure) result).getMessage());
      }
    } catch (Exception e) {
      failCount[0]++;
      log.warn("Failed to load DSL script '{}': {}", fileName, e.getMessage());
    }
  }

  private void mergeRegistry(DslRegistry loaded, DslRegistry shared) {
    loaded.getWorkflows().values().forEach(shared::register);
    loaded.getEvents().values().forEach(shared::register);
    loaded.getTransactions().values().forEach(shared::register);
    loaded.getMassOperations().values().forEach(shared::register);
    loaded.getHelpers().values().forEach(shared::register);
    loaded.getConditions().values().forEach(shared::register);
  }
}
