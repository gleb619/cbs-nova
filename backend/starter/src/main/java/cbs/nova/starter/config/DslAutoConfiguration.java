package cbs.nova.starter.config;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.TemporalProcessLauncher;
import cbs.nova.dsl.config.DslConfig;
import cbs.nova.starter.ExternalCallTracker;
import cbs.nova.starter.listeners.ExternalCallListener;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@AutoConfiguration
public class DslAutoConfiguration {

  @Value("${dsl.source-dir:}")
  private String sourceDirProperty;

  @Autowired(required = false)
  private List<ExternalCallListener> externalCallListeners;

  @Autowired(required = false)
  private ExternalCallTracker externalCallTracker;

  @Autowired(required = false)
  private TemporalProcessLauncher temporalProcessLauncher;

  @PostConstruct
  public void loadDslDefinitions() {
    if (sourceDirProperty != null && !sourceDirProperty.isBlank()) {
      var dir = Path.of(sourceDirProperty);
      if (!Files.isDirectory(dir)) {
        throw new IllegalStateException(
                "dsl.source-dir does not exist or is not a directory: " + dir);
      }

      new DefinitionLoader().load(dir, GlobalManager.getInstance());
    }
    registerHelperResolvers();
    registerExternalCallListeners();
    registerTemporalProcessLauncher();
  }

  private void registerHelperResolvers() {
    GlobalManager.getInstance().registerHelperResolvers();
  }

  private void registerExternalCallListeners() {
    if (externalCallListeners == null || externalCallListeners.isEmpty()
            || externalCallTracker == null) {
      return;
    }

    for (ExternalCallListener listener : externalCallListeners) {
      externalCallTracker.registerListener(listener);
    }
  }

  private void registerTemporalProcessLauncher() {
    if (temporalProcessLauncher != null) {
      DslConfig.dslConfig().temporalProcessLauncher().replace(temporalProcessLauncher);
    }
  }
}
