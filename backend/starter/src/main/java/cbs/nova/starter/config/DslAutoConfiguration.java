package cbs.nova.starter.config;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Helper;
import cbs.nova.starter.ExternalCallTracker;
import cbs.nova.starter.listeners.ExternalCallListener;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@AutoConfiguration
public class DslAutoConfiguration {

  @Value("${dsl.source-dir:}")
  private String sourceDirProperty;

  @Value("${dsl.helper-scan-packages:}")
  private String helperScanPackages;

  @Autowired(required = false)
  private List<ExternalCallListener> externalCallListeners;

  @Autowired(required = false)
  private ExternalCallTracker externalCallTracker;

  @PostConstruct
  void loadDslDefinitions() {
    if (sourceDirProperty != null && !sourceDirProperty.isBlank()) {
      var dir = Path.of(sourceDirProperty);
      if (!Files.isDirectory(dir)) {
        throw new IllegalStateException(
                "dsl.source-dir does not exist or is not a directory: " + dir);
      }
      new DefinitionLoader().load(dir, GlobalManager.getInstance());
    }
    scanHelpers();
    registerExternalCallListeners();
  }

  private void scanHelpers() {
    if (helperScanPackages == null || helperScanPackages.isBlank()) {
      return;
    }
    var scanner = new ClassPathScanningCandidateComponentProvider(false);
    scanner.addIncludeFilter(new AnnotationTypeFilter(Helper.class));
    var gm = GlobalManager.getInstance();
    for (String pkg : helperScanPackages.split(",")) {
      pkg = pkg.strip();
      if (pkg.isBlank()) {
        continue;
      }
      for (var bd : scanner.findCandidateComponents(pkg)) {
        try {
          Class<?> cls = Class.forName(bd.getBeanClassName());
          Helper annotation = cls.getAnnotation(Helper.class);
          if (!Executable.class.isAssignableFrom(cls)) {
            log.warn("[DslAutoConfiguration] @Helper class not Executable: {}", cls.getName());
            continue;
          }
          var ctor = cls.getDeclaredConstructor();
          ctor.setAccessible(true);
          Executable<?, ?> instance = (Executable<?, ?>) ctor.newInstance();
          gm.registerHelper(annotation.name(), instance);
        } catch (Exception e) {
          log.error(
                  "[DslAutoConfiguration] Failed to register @Helper: {}: {}",
                  bd.getBeanClassName(),
                  e.getMessage(),
                  e);
        }
      }
    }
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
}
