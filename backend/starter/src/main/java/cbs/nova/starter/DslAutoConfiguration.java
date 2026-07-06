package cbs.nova.starter;

import cbs.nova.dsl.DefinitionLoader;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.GlobalManager;
import cbs.nova.dsl.Helper;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@AutoConfiguration
public class DslAutoConfiguration {

  @Value("${dsl.source-dir:}")
  private String sourceDirProperty;

  @Value("${dsl.helper-scan-packages:}")
  private String helperScanPackages;

  @Autowired(required = false)
  private List<ExternalCallListener> externalCallListeners;

  @PostConstruct
  void loadDslDefinitions() {
    if (sourceDirProperty != null && !sourceDirProperty.isBlank()) {
      var dir = Path.of(sourceDirProperty);
      if (!Files.isDirectory(dir)) {
        throw new IllegalStateException(
                "dsl.source-dir does not exist or is not a directory: " + dir);
      }
      DefinitionLoader.load(dir, GlobalManager.getInstance());
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
            System.err.println(
                    "[DslAutoConfiguration] @Helper class not Executable: " + cls.getName());
            continue;
          }
          var ctor = cls.getDeclaredConstructor();
          ctor.setAccessible(true);
          Executable<?, ?> instance = (Executable<?, ?>) ctor.newInstance();
          gm.registerHelper(annotation.name(), instance);
        } catch (Exception e) {
          System.err.println(
                  "[DslAutoConfiguration] Failed to register @Helper: "
                          + bd.getBeanClassName()
                          + ": "
                          + e.getMessage());
        }
      }
    }
  }

  /**
   * Automatically registers all ExternalCallListener beans with the ExternalCallTracker to enable
   * automatic tracking of external calls during DSL execution.
   */
  private void registerExternalCallListeners() {
    if (externalCallListeners == null || externalCallListeners.isEmpty()) {
      return;
    }

    ExternalCallTracker tracker = ExternalCallTracker.getInstance();
    if (tracker == null) {
      // Tracker not initialized yet, listeners will be registered when it is
      return;
    }

    for (ExternalCallListener listener : externalCallListeners) {
      tracker.registerListener(listener);
    }
  }
}
