package cbs.nova.dsl.builder.service;

import cbs.nova.dsl.builder.config.DslBuilderProperties;
import cbs.nova.dsl.builder.exception.CompileException;
import cbs.nova.dsl.builder.model.CompileModels.CompileRequest;
import cbs.nova.dsl.builder.model.CompileModels.CompileResult;
import cbs.nova.dsl.builder.model.CompileSession;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompileService {

  private static final Pattern PACKAGE_PATTERN = Pattern.compile(
          "^[a-zA-Z_$][\\w$]*(\\.[a-zA-Z_$][\\w$]*)*$");
  private static final String SETTINGS_TEMPLATE = "settings.gradle.template";
  private static final String BUILD_TEMPLATE = "build.gradle.template";

  private final DslBuilderProperties properties;
  private final GitService gitService;
  private final GradleService gradleService;
  private final ResourceLoader resourceLoader;
  private final BuilderWorkQueue workQueue;
  private final Map<String, CompileSession> sessions = new ConcurrentHashMap<>();

  private String settingsTemplate = "";
  private String buildTemplate = "";

  @PostConstruct
  void loadTemplates() {
    var dir = properties.templatesDir();
    settingsTemplate = readTemplate(dir, SETTINGS_TEMPLATE);
    buildTemplate = readTemplate(dir, BUILD_TEMPLATE);
    log.info("Loaded DSL build templates from {}", dir);
  }

  public CompileResult compile(CompileRequest request) {
    return workQueue.submit(() -> compileInternal(request));
  }

  private CompileResult compileInternal(CompileRequest request) {
    validatePackage(request.targetPackage(), "targetPackage");
    validatePackage(request.basePackage(), "basePackage");
    var repoUrl = firstNonBlank(request.repoUrl(), properties.git().repoUrl());
    if (isBlank(repoUrl) && (request.sources() == null || request.sources().isEmpty())) {
      throw new IllegalArgumentException("Either sources or a git repository must be provided");
    }
    if (!isBlank(request.repoUrl())) {
      // SSRF guard: only the request-supplied URL is attacker-controlled; the configured
      // fallback (properties.git().repoUrl()) is trusted deployment configuration.
      RepoUrlValidator.validate(request.repoUrl(), properties);
    }
    var session = createSession();
    try {
      scaffoldProject(session, request);
      if (!isBlank(repoUrl)) {
        var repoDir = session.getSessionDir().resolve("repo");
        gitService.cloneRepository(
                repoUrl, repoDir, firstNonBlank(request.baseBranch(), properties.git().branch()));
        copyRepoSources(repoDir, session.srcDir());
      }
      if (request.sources() != null && !request.sources().isEmpty()) {
        writeSources(session, request.sources());
      }
      return runGradleBuild(session, request);
    } catch (IOException e) {
      deleteSession(session);
      throw new CompileException("Failed to stage DSL build: " + e.getMessage(), e);
    }
  }

  public Optional<CompileSession> findSession(String id) {
    return Optional.ofNullable(sessions.get(id));
  }

  public void deleteSession(CompileSession session) {
    sessions.remove(session.getId());
    deleteRecursively(session.getSessionDir());
  }

  @Scheduled(fixedDelayString = "${cbs.dsl.builder.cleanup-interval:PT10M}")
  public void cleanupExpiredSessions() {
    var now = Instant.now();
    var ttl = properties.sessionTtl();
    sessions.entrySet().removeIf(entry -> {
      var session = entry.getValue();
      if (!session.isExpired(ttl, now)) {
        return false;
      }
      deleteRecursively(session.getSessionDir());
      log.info("Expired compile session {} deleted", session.getId());
      return true;
    });
  }

  /* ============= */

  private CompileResult runGradleBuild(CompileSession session, CompileRequest request) {
    var start = Instant.now();
    var outcome = gradleService.runBuild(session.getProjectDir(), properties.buildTasks());
    if (!outcome.success()) {
      deleteSession(session);
      var diagnostics = outcome.logLines().isEmpty()
              ? List.of("Gradle build failed")
              : outcome.logLines();
      throw new CompileException("Gradle build failed", diagnostics);
    }
    var generatedFiles = collectGeneratedFiles(session.getOutputDir());
    var durationMillis = Duration.between(start, Instant.now()).toMillis();
    return new CompileResult(session.getId(), true, generatedFiles, List.of(), durationMillis);
  }

  private List<String> collectGeneratedFiles(Path outputDir) {
    try (var walk = Files.walk(outputDir)) {
      return walk.filter(Files::isRegularFile)
              .map(outputDir::relativize)
              .map(path -> path.toString().replace('\\', '/'))
              .sorted()
              .toList();
    } catch (IOException e) {
      throw new CompileException("Failed to collect generated files: " + e.getMessage(), e);
    }
  }

  private void scaffoldProject(CompileSession session, CompileRequest request)
          throws IOException {
    Files.createDirectories(session.srcDir());
    Files.writeString(session.getProjectDir().resolve("settings.gradle"), settingsTemplate);
    Files.writeString(session.getProjectDir().resolve("build.gradle"), renderBuildGradle(request));
  }

  private String renderBuildGradle(CompileRequest request) {
    var useFileNameSubPackage = request.useFileNameSubPackage() != null
            && request.useFileNameSubPackage();
    return buildTemplate
            .replace("__DSL_VERSION__", properties.dslVersion())
            .replace("__TEMPORAL_VERSION__", properties.temporalVersion())
            .replace("__SPRING_BOOT_VERSION__", properties.springBootVersion())
            .replace("__TARGET_PACKAGE__", groovyString(request.targetPackage()))
            .replace("__BASE_PACKAGE__", groovyString(request.basePackage()))
            .replace("__BUILD_VERSION__", groovyString(resolveBuildVersion(request.buildVersion())))
            .replace("__USE_FILENAME_SUB_PACKAGE__", Boolean.toString(useFileNameSubPackage));
  }

  private String readTemplate(String dir, String name) {
    var location = "classpath:" + trimTrailingSlash(dir) + "/" + name;
    Resource resource = resourceLoader.getResource(location);
    if (!resource.exists()) {
      throw new IllegalStateException("DSL build template not found: " + location);
    }
    try (InputStream in = resource.getInputStream()) {
      return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read DSL build template: " + location, e);
    }
  }

  private String trimTrailingSlash(String value) {
    if (value == null || value.isEmpty()) {
      return "";
    }
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private CompileSession createSession() {
    var id = UUID.randomUUID().toString();
    var sessionDir = properties.workspaceDir().resolve(id);
    var projectDir = sessionDir.resolve("project");
    var session = new CompileSession(
            id, sessionDir, projectDir, projectDir.resolve("build").resolve("generated"),
            Instant.now());
    try {
      Files.createDirectories(session.srcDir());
    } catch (IOException e) {
      throw new CompileException("Failed to create compile session workspace: " + e.getMessage(),
              e);
    }
    sessions.put(id, session);
    return session;
  }

  private void writeSources(CompileSession session, Map<String, String> sources)
          throws IOException {
    var srcDir = session.srcDir();
    for (var entry : sources.entrySet()) {
      var target = resolveWithin(srcDir, entry.getKey());
      Files.createDirectories(target.getParent());
      Files.writeString(target, entry.getValue());
    }
  }

  private void copyRepoSources(Path repoDir, Path srcDir) throws IOException {
    var subPath = properties.git().subPath();
    var base = isBlank(subPath) ? repoDir : repoDir.resolve(subPath);
    var repoSrc = base.resolve("src");
    var sourceBase = Files.isDirectory(repoSrc) ? repoSrc : base;
    for (var folder : properties.sourceFolders()) {
      var source = sourceBase.resolve(folder);
      if (Files.isDirectory(source)) {
        copyDirectory(source, srcDir.resolve(folder));
      }
    }
  }

  private void copyDirectory(Path source, Path target) throws IOException {
    try (var walk = Files.walk(source)) {
      for (var path : walk.toList()) {
        var destination = target.resolve(source.relativize(path).toString());
        if (Files.isDirectory(path)) {
          Files.createDirectories(destination);
        } else {
          Files.createDirectories(destination.getParent());
          Files.copy(path, destination, StandardCopyOption.REPLACE_EXISTING);
        }
      }
    }
  }

  private Path resolveWithin(Path baseDir, String relativePath) {
    var normalized = baseDir.resolve(relativePath).normalize();
    if (!normalized.startsWith(baseDir.normalize())) {
      throw new IllegalArgumentException("Source path escapes the session directory: "
              + relativePath);
    }
    return normalized;
  }

  private void validatePackage(String packageName, String field) {
    if (packageName != null && !PACKAGE_PATTERN.matcher(packageName).matches()) {
      throw new IllegalArgumentException("Invalid " + field + ": " + packageName);
    }
  }

  private String resolveBuildVersion(String buildVersion) {
    return isBlank(buildVersion) ? properties.defaultBuildVersion() : buildVersion;
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private String firstNonBlank(String first, String second) {
    return !isBlank(first) ? first : second;
  }

  private String groovyString(String value) {
    return value == null ? "" : value.replace("\\", "\\\\").replace("'", "\\'");
  }

  private void deleteRecursively(Path dir) {
    if (!Files.exists(dir)) {
      return;
    }
    try (var walk = Files.walk(dir)) {
      walk.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.delete(path);
        } catch (IOException e) {
          log.warn("Failed to delete {}: {}", path, e.getMessage());
        }
      });
    } catch (IOException e) {
      log.warn("Failed to walk {}: {}", dir, e.getMessage());
    }
  }
}
