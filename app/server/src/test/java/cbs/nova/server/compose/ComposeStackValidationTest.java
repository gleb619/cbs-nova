package cbs.nova.server.compose;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Guards the {@code app/compose} stack (per-domain includes, shared postgres, initdb scripts)
 * with automated validation so a broken include, missing env var, or renamed database is
 * caught by a test rather than at {@code docker compose up}.
 *
 * <p>The test does three things:
 * <ol>
 *   <li>Runs {@code docker compose -f app/docker-compose.yml config} against the repo
 *       root and fails on non-zero exit or unresolved-interpolation warnings such as
 *       {@code "variable is not set"}.</li>
 *   <li>Syntax-checks each initdb script under {@code app/compose/postgres-initdb.d/}
 *       with {@code bash -n} (no execution).</li>
 *   <li>Cross-checks the databases and roles that the initdb scripts create against
 *       the per-service compose files (keycloak, gitea, bugsink, temporal).</li>
 * </ol>
 *
 * <p>If {@code docker} is not on {@code PATH} or the {@code compose} subcommand is not
 * usable, the docker steps are skipped via JUnit 5 {@link
 * org.junit.jupiter.api.Assumptions Assumptions} — the build is not failed in
 * environments without a Docker daemon.
 */
class ComposeStackValidationTest {

  /** Marker the Docker compose CLI emits when a referenced env var has no default. */
  private static final String INTERPOLATION_WARNING_MARKER = "variable is not set";

  /** Marker for env vars that have a default in the compose file. */
  private static final Pattern DEFAULTED_VAR =
      Pattern.compile("\\$\\{([A-Z_][A-Z0-9_]*):-([^}]*)\\}");

  /** Marker for env vars that have no default. */
  private static final Pattern UNDEFAULTED_VAR = Pattern.compile("\\$\\{([A-Z_][A-Z0-9_]*)\\}");

  @Test
  @DisplayName("docker compose config validates the full stack without warnings")
  void dockerComposeConfigIsClean() throws IOException, InterruptedException {
    Path repoRoot = locateRepoRoot();
    Path composeFile = repoRoot.resolve("app/docker-compose.yml");
    assertThat(composeFile).as("docker-compose.yml").exists();

    assumeDockerAvailable();

    ProcessResult result = run(
        List.of("docker", "compose", "-f", composeFile.toString(), "config"),
        repoRoot);

    assertThat(result.exitCode())
        .as("docker compose config exit code\n--- stdout ---\n%s\n--- stderr ---\n%s",
            result.stdout(), result.stderr())
        .isEqualTo(0);

    String combined = result.stdout() + "\n" + result.stderr();
    assertThat(combined.toLowerCase(Locale.ROOT))
        .as("docker compose config output must not contain interpolation warnings")
        .doesNotContain(INTERPOLATION_WARNING_MARKER);
  }

  @Test
  @DisplayName("postgres initdb scripts pass bash -n syntax check")
  void initdbScriptsPassBashSyntaxCheck() throws IOException, InterruptedException {
    Path repoRoot = locateRepoRoot();
    Path initdbDir = repoRoot.resolve("app/compose/postgres-initdb.d");
    assertThat(initdbDir).as("postgres-initdb.d directory").exists().isDirectory();

    List<Path> scripts;
    try (var stream = Files.list(initdbDir)) {
      scripts = stream.filter(p -> p.getFileName().toString().endsWith(".sh")).sorted().toList();
    }
    assertThat(scripts).as("initdb scripts").isNotEmpty();

    for (Path script : scripts) {
      ProcessResult result = run(List.of("bash", "-n", script.toString()), repoRoot);
      assertThat(result.exitCode())
          .as("bash -n exit code for %s\n--- stdout ---\n%s\n--- stderr ---\n%s",
              script.getFileName(), result.stdout(), result.stderr())
          .isEqualTo(0);
    }
  }

  @Test
  @DisplayName("initdb-created databases and roles match per-service compose expectations")
  void initdbDbsAndRolesMatchComposeExpectations() throws IOException {
    Path repoRoot = locateRepoRoot();
    Path initdbDir = repoRoot.resolve("app/compose/postgres-initdb.d");
    Path composeDir = repoRoot.resolve("app/compose");

    DbUserMap initdb = parseInitdbScripts(initdbDir);
    assertThat(initdb.databases())
        .as("databases created by initdb scripts")
        .contains("keycloak", "gitea", "bugsink", "temporal");
    assertThat(initdb.users())
        .as("users created by initdb scripts")
        .contains("keycloak", "gitea", "bugsinkuser", "temporal");

    String auth = Files.readString(composeDir.resolve("auth.yml"), StandardCharsets.UTF_8);
    String gitea = Files.readString(composeDir.resolve("gitea.yml"), StandardCharsets.UTF_8);
    String bugsink = Files.readString(composeDir.resolve("error-tracking.yml"), StandardCharsets.UTF_8);
    String orchestration = Files.readString(composeDir.resolve("orchestration.yml"), StandardCharsets.UTF_8);

    // keycloak -> KC_DB_URL_DATABASE / KC_DB_USERNAME
    assertThat(auth)
        .as("auth.yml must declare KC_DB_URL_DATABASE=keycloak")
        .containsPattern(Pattern.quote("KC_DB_URL_DATABASE: keycloak"));
    assertThat(auth)
        .as("auth.yml must declare KC_DB_USERNAME=keycloak")
        .containsPattern(Pattern.quote("KC_DB_USERNAME: keycloak"));
    assertThat(initdb.databases()).contains("keycloak");
    assertThat(initdb.users()).contains("keycloak");

    // gitea -> GITEA__database__NAME / GITEA__database__USER
    assertThat(gitea)
        .as("gitea.yml must declare GITEA__database__NAME=gitea")
        .containsPattern(Pattern.quote("GITEA__database__NAME: gitea"));
    assertThat(gitea)
        .as("gitea.yml must declare GITEA__database__USER=gitea")
        .containsPattern(Pattern.quote("GITEA__database__USER: gitea"));
    assertThat(initdb.databases()).contains("gitea");
    assertThat(initdb.users()).contains("gitea");

    // bugsink -> DATABASE_URL=postgresql://bugsinkuser:...@postgres:5432/bugsink
    String bugsinkDbUrl = extractBugsinkDatabaseUrl(bugsink);
    assertThat(bugsinkDbUrl)
        .as("error-tracking.yml must declare a postgres DATABASE_URL")
        .startsWith("postgresql://");
    assertThat(bugsinkDbUrl)
        .as("DATABASE_URL user segment must be 'bugsinkuser'")
        .contains("://bugsinkuser:");
    assertThat(bugsinkDbUrl)
        .as("DATABASE_URL database segment must be 'bugsink'")
        .matches(".*@postgres:\\d+/bugsink$");
    assertThat(initdb.databases()).contains("bugsink");
    assertThat(initdb.users()).contains("bugsinkuser");

    // temporal -> POSTGRES_USER=temporal (auto-setup defaults to DB name 'temporal')
    assertThat(orchestration)
        .as("orchestration.yml must declare POSTGRES_USER=temporal")
        .containsPattern(Pattern.quote("POSTGRES_USER: temporal"));
    assertThat(initdb.databases()).contains("temporal");
    assertThat(initdb.users()).contains("temporal");
  }

  @Test
  @DisplayName("compose env vars consumed by initdb have defaults in postgres.yml")
  void initdbConsumedEnvVarsHaveDefaults() throws IOException {
    Path repoRoot = locateRepoRoot();
    Path postgres = repoRoot.resolve("app/compose/postgres.yml");
    String postgresText = Files.readString(postgres, StandardCharsets.UTF_8);

    // The initdb script reads these per-service password env vars (with defaults in the
    // script itself). The shared postgres service should also expose them with defaults
    // so docker compose never emits "variable is not set" warnings at config time.
    for (String var : List.of("KEYCLOAK_DB_PASSWORD", "GITEA_DB_PASSWORD",
        "BUGSINK_DB_PASSWORD", "TEMPORAL_DB_PASSWORD", "POSTGRES_PASSWORD")) {
      Matcher matcher = DEFAULTED_VAR.matcher(postgresText);
      boolean hasDefault = false;
      while (matcher.find()) {
        if (matcher.group(1).equals(var)) {
          hasDefault = true;
          break;
        }
      }
      assertThat(hasDefault)
          .as("postgres.yml must give %s a default value (${%s:-...})", var, var)
          .isTrue();
    }
  }

  @Test
  @DisplayName("no undefaulted ${VAR} references in any included compose file")
  void noUndefaultedEnvVarInCompose() throws IOException {
    Path repoRoot = locateRepoRoot();
    Path composeFile = repoRoot.resolve("app/docker-compose.yml");
    String text = Files.readString(composeFile, StandardCharsets.UTF_8);

    // The top-level include: list is the source of truth for which files participate
    // in the merged project. We parse it and then check each included file plus the
    // root file itself for any ${VAR} reference that lacks a :- default.
    List<Path> includes = parseIncludePaths(text, repoRoot);
    List<Path> allFiles = new ArrayList<>();
    allFiles.add(composeFile);
    allFiles.addAll(includes);

    for (Path file : allFiles) {
      String content = Files.readString(file, StandardCharsets.UTF_8);
      Matcher m = UNDEFAULTED_VAR.matcher(content);
      List<String> offenders = new ArrayList<>();
      while (m.find()) {
        offenders.add(m.group(1));
      }
      assertThat(offenders)
          .as("undefaulted ${VAR} references in %s — every env var must use ${VAR:-default}",
              file.getFileName())
          .isEmpty();
    }
  }

  // -----------------------------------------------------------------------------------
  // Helpers
  // -----------------------------------------------------------------------------------

  /** Walks up from the current working directory until it finds {@code app/docker-compose.yml}. */
  private static Path locateRepoRoot() {
    Path cwd = Paths.get("").toAbsolutePath();
    Path candidate = cwd;
    while (candidate != null) {
      if (Files.isRegularFile(candidate.resolve("app/docker-compose.yml"))) {
        return candidate;
      }
      candidate = candidate.getParent();
    }
    throw new IllegalStateException(
        "Could not locate repo root from " + cwd + " (no app/docker-compose.yml found)");
  }

  /**
   * Skips the test when {@code docker} is not on {@code PATH} or the {@code compose}
   * subcommand is not usable. {@code Assumptions#assumeTrue} aborts the current test
   * (reported as skipped) without failing the rest of the suite.
   */
  private static void assumeDockerAvailable() throws IOException, InterruptedException {
    ProcessResult which = run(List.of("sh", "-c", "command -v docker"), Paths.get("").toAbsolutePath());
    assumeTrue(which.exitCode() == 0 && !which.stdout().isBlank(),
        () -> "docker is not on PATH — skipping compose validation");

    ProcessResult version = run(
        List.of("docker", "compose", "version", "--short"),
        Paths.get("").toAbsolutePath());
    assumeTrue(version.exitCode() == 0,
        () -> "docker compose subcommand is not available (exit "
            + version.exitCode() + ", stderr=" + version.stderr().trim() + ")");
  }

  /** Runs the given command in {@code cwd}, waits for it to finish, and captures stdout + stderr. */
  private static ProcessResult run(List<String> command, Path cwd)
      throws IOException, InterruptedException {
    ProcessBuilder pb = new ProcessBuilder(command).directory(cwd.toFile());
    pb.redirectErrorStream(false);
    Process process = pb.start();
    String stdout = drain(process.getInputStream());
    String stderr = drain(process.getErrorStream());
    boolean finished = process.waitFor(120, TimeUnit.SECONDS);
    if (!finished) {
      process.destroyForcibly();
      throw new IOException("command timed out: " + String.join(" ", command));
    }
    return new ProcessResult(process.exitValue(), stdout, stderr);
  }

  private static String drain(InputStream stream) throws IOException {
    byte[] bytes = stream.readAllBytes();
    return new String(bytes, StandardCharsets.UTF_8);
  }

  /** Parses every {@code CREATE DATABASE x} and {@code CREATE USER x} from initdb scripts. */
  private static DbUserMap parseInitdbScripts(Path initdbDir) throws IOException {
    Map<String, String> dbToUser = new LinkedHashMap<>();
    Pattern createDb = Pattern.compile("CREATE\\s+DATABASE\\s+(\\w+)",
        Pattern.CASE_INSENSITIVE);
    Pattern createUser = Pattern.compile("CREATE\\s+USER\\s+(\\w+)",
        Pattern.CASE_INSENSITIVE);

    try (var stream = Files.list(initdbDir)) {
      List<Path> scripts = stream.filter(p -> p.getFileName().toString().endsWith(".sh")).sorted().toList();
      for (Path script : scripts) {
        String text = Files.readString(script, StandardCharsets.UTF_8);
        List<String> dbs = new ArrayList<>();
        List<String> users = new ArrayList<>();
        Matcher mDb = createDb.matcher(text);
        while (mDb.find()) {
          dbs.add(mDb.group(1).toLowerCase(Locale.ROOT));
        }
        Matcher mUser = createUser.matcher(text);
        while (mUser.find()) {
          users.add(mUser.group(1).toLowerCase(Locale.ROOT));
        }
        // The initdb script declares DBs and users in paired order; zip them so we can
        // sanity-check the mapping without trusting textual proximity alone.
        int pairs = Math.min(dbs.size(), users.size());
        for (int i = 0; i < pairs; i++) {
          dbToUser.put(dbs.get(i), users.get(i));
        }
      }
    }

    return new DbUserMap(
        dbToUser.keySet().stream().sorted().toList(),
        dbToUser.values().stream().distinct().sorted().toList(),
        dbToUser);
  }

  /** Extracts the {@code DATABASE_URL} value from the bugsink service block. */
  private static String extractBugsinkDatabaseUrl(String bugsinkCompose) {
    Pattern p = Pattern.compile("DATABASE_URL:\\s*(\\S+)");
    Matcher m = p.matcher(bugsinkCompose);
    if (!m.find()) {
      throw new AssertionError(
          "error-tracking.yml has no DATABASE_URL: " + bugsinkCompose);
    }
    // Strip surrounding quotes if present.
    String value = m.group(1);
    if ((value.startsWith("\"") && value.endsWith("\""))
        || (value.startsWith("'") && value.endsWith("'"))) {
      value = value.substring(1, value.length() - 1);
    }
    return value;
  }

  /** Parses {@code include: - path: ...} entries from the top-level compose file. */
  private static List<Path> parseIncludePaths(String dockerCompose, Path repoRoot) {
    Pattern p = Pattern.compile("^\\s*-\\s*path:\\s*(\\S+)\\s*$", Pattern.MULTILINE);
    Matcher m = p.matcher(dockerCompose);
    List<Path> out = new ArrayList<>();
    while (m.find()) {
      String raw = m.group(1);
      // Drop optional YAML quoting.
      if ((raw.startsWith("\"") && raw.endsWith("\""))
          || (raw.startsWith("'") && raw.endsWith("'"))) {
        raw = raw.substring(1, raw.length() - 1);
      }
      // The include paths in app/docker-compose.yml are relative to that file's
      // directory, which is the repo root.
      out.add(repoRoot.resolve("app").resolve(raw).normalize());
    }
    return out;
  }

  private record DbUserMap(List<String> databases, List<String> users, Map<String, String> dbToUser) {
  }

  private record ProcessResult(int exitCode, String stdout, String stderr) {
  }
}
