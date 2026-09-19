package cbs.nova.starter.service;

import java.util.LinkedHashSet;

import static cbs.nova.starter.core.StarterConstants.ACTION_MANIFEST_RELOAD;

import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.exception.PieceManifestValidationException;
import cbs.nova.starter.model.ManifestReloadResponse;
import cbs.nova.starter.model.ObjectAllow;
import cbs.nova.starter.model.ObjectDeny;
import cbs.nova.starter.model.Piece;
import cbs.nova.starter.model.PieceManifest;
import cbs.nova.starter.model.PostCheck;
import cbs.nova.starter.model.PreCheck;
import cbs.nova.starter.model.Target;
import cbs.nova.starter.security.Role;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Loads and validates the piece-manifest YAML at startup, then serves as the read-only lookup index
 * for the enforcement layers (T549/T550/T552).
 *
 * <h2>Failure semantics</h2> When the configured path is blank or the resource does not exist the
 * service logs once and keeps an empty snapshot — existing deployments are unaffected. A malformed
 * manifest aborts startup with a {@link PieceManifestValidationException} naming the offending
 * piece id and field.
 *
 * <h2>Reload</h2> {@link #reload(ServerRequest)} builds a complete candidate snapshot and validates
 * it before swapping the live reference. If validation fails the previous snapshot stays live and
 * the response carries per-piece errors. A {@link ReentrantLock} serializes overlapping reload
 * calls.
 */
@Slf4j
public class PieceManifestService {

  private static final Set<String> VALID_FAIL_MODES = Set.of("deny", "audit-only");
  private static final Set<String> VALID_ON_FAILURE = Set.of(
          PostCheck.ON_FAILURE_WARN, PostCheck.ON_FAILURE_BLOCK);
  private static final Set<String> VALID_OBJECT_TYPES = Set.of("helper", "process", "function");
  private static final Set<String> VALID_ROLES = Stream.of(Role.values())
          .map(r -> r.name().toLowerCase(Locale.ROOT))
          .collect(Collectors.toUnmodifiableSet());
  private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9]+(-[a-z0-9]+)*$");
  private static final Pattern ROUTE_PATTERN = Pattern.compile("^[A-Z]+ .+$");

  private final CbsDslManifestProperties properties;
  private final ResourceLoader resourceLoader;
  private final ObjectProvider<DslAuditService> auditServiceProvider;
  private final @Nullable ObjectProvider<PieceCheckBlockRegistry> blockRegistryProvider;
  private final ReentrantLock reloadLock = new ReentrantLock();
  private final AntPathMatcher pathMatcher = new AntPathMatcher();
  private volatile Snapshot snapshot;

  public PieceManifestService(CbsDslManifestProperties properties,
          ResourceLoader resourceLoader,
          ObjectProvider<DslAuditService> auditServiceProvider) {
    this(properties, resourceLoader, auditServiceProvider, null);
  }

  public PieceManifestService(CbsDslManifestProperties properties,
          ResourceLoader resourceLoader,
          ObjectProvider<DslAuditService> auditServiceProvider,
          @Nullable ObjectProvider<PieceCheckBlockRegistry> blockRegistryProvider) {
    this.properties = Objects.requireNonNull(properties, "properties required");
    this.resourceLoader = resourceLoader == null
            ? new org.springframework.core.io.DefaultResourceLoader()
            : resourceLoader;
    this.auditServiceProvider = auditServiceProvider;
    this.blockRegistryProvider = blockRegistryProvider;
    this.snapshot = loadSnapshot(properties.path());
  }

  /**
   * Looks up a piece by its stable kebab-case id.
   */
  public Optional<Piece> find(String id) {
    return Optional.ofNullable(snapshot.byId.get(id));
  }

  /**
   * Returns all pieces whose target has the same discriminated type as {@code target}. Passing an
   * {@link Target.ApiTarget} returns every API piece; a {@link Target.ButtonTarget} returns every
   * button piece, etc.
   */
  public List<Piece> byTarget(Target target) {
    return snapshot.byTargetType.getOrDefault(target.type(), List.of());
  }

  /**
   * Returns all object-target pieces whose {@code objectType} and {@code objectName} match exactly.
   */
  public List<Piece> findByObject(String objectType, String objectName) {
    return snapshot.objectPieces.stream()
            .filter(p -> p.target() instanceof Target.ObjectTarget ot
                    && ot.objectType().equals(objectType)
                    && ot.objectName().equals(objectName))
            .toList();
  }

  /**
   * Finds the first API piece whose route matches the given HTTP method and request path using the
   * same ant-pattern matching idiom as the RBAC filter.
   */
  public Optional<Piece> findByRoute(String method, String path) {
    String methodUpper = method == null ? "" : method.toUpperCase(Locale.ROOT);
    for (Piece piece : snapshot.apiPieces) {
      if (piece.target() instanceof Target.ApiTarget api) {
        Route route = Route.parse(api.route());
        if (route.method().equals(methodUpper) && pathMatcher.match(route.path(), path)) {
          return Optional.of(piece);
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Hot-reloads the manifest from the configured path. Builds and validates a candidate snapshot,
   * then atomically swaps it into place. On failure the previous snapshot remains live and an audit
   * row is written when {@link DslAuditService} is present.
   */
  public ServerResponse reload(ServerRequest request) {
    if (!properties.enabled()) {
      return error(HttpStatus.CONFLICT, "MANIFEST_RELOAD_DISABLED",
              "cbs.dsl.manifest.enabled is false");
    }
    if (properties.path() == null || properties.path().isBlank()) {
      return error(HttpStatus.CONFLICT, "MANIFEST_NOT_CONFIGURED",
              "cbs.dsl.manifest.path is not configured");
    }

    reloadLock.lock();
    try {
      var candidate = loadSnapshot(properties.path());
      var previous = snapshot;
      snapshot = candidate;
      int count = candidate.pieces.size();
      log.info("[Manifest reload] swapped snapshot: {} piece(s) loaded", count);
      clearBlockRegistry();
      audit(request, properties.path(), StarterConstants.OUTCOME_SUCCESS,
              Map.of("pieceCount", count));
      return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(new ManifestReloadResponse(count, List.of()));
    } catch (PieceManifestValidationException e) {
      log.error("[Manifest reload] rejected: {}", e.getMessage());
      audit(request, properties.path(), StarterConstants.OUTCOME_FAILURE,
              Map.of("errors", e.errors()));
      return ServerResponse.status(HttpStatus.BAD_REQUEST)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new ManifestReloadResponse(0, e.errors()));
    } catch (RuntimeException e) {
      log.error("[Manifest reload] failed", e);
      audit(request, properties.path(), StarterConstants.OUTCOME_FAILURE,
              Map.of("error", String.valueOf(e.getMessage())));
      return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
              .contentType(MediaType.APPLICATION_JSON)
              .body(new ManifestReloadResponse(0, List.of(
                      new ManifestReloadResponse.ErrorEntry("-", "load",
                              e.getMessage() == null ? e.getClass().getName() : e.getMessage()))));
    } finally {
      reloadLock.unlock();
    }
  }

  /**
   * Programmatic reload for callers that want the result directly. Shares the same lock and failure
   * semantics as {@link #reload(ServerRequest)}.
   *
   * @return the reloaded piece manifest
   * @throws PieceManifestValidationException
   *           if the document is invalid
   */
  public PieceManifest reloadManifest() {
    if (properties.path() == null || properties.path().isBlank()) {
      throw new IllegalStateException("cbs.dsl.manifest.path is not configured");
    }
    reloadLock.lock();
    try {
      var candidate = loadSnapshot(properties.path());
      snapshot = candidate;
      clearBlockRegistry();
      return new PieceManifest(candidate.pieces);
    } finally {
      reloadLock.unlock();
    }
  }

  /**
   * A successful reload is the operator's "reviewed and fixed" signal: all
   * {@code block-next-execution} blocks clear so the reloaded manifest governs from scratch.
   */
  private void clearBlockRegistry() {
    if (blockRegistryProvider == null) {
      return;
    }
    PieceCheckBlockRegistry registry = blockRegistryProvider.getIfAvailable();
    if (registry != null) {
      registry.clear();
    }
  }

  Snapshot snapshot() {
    return snapshot;
  }

  private Snapshot loadSnapshot(String path) {
    if (path == null || path.isBlank()) {
      log.info("[Manifest] no path configured — starting with empty piece snapshot");
      return Snapshot.empty();
    }

    Resource resource = resourceLoader.getResource(path);
    if (!resource.exists() || !resource.isReadable()) {
      log.info("[Manifest] resource not readable at '{}' — starting with empty piece snapshot",
              path);
      return Snapshot.empty();
    }

    Map<String, Object> root;
    try (InputStream is = resource.getInputStream()) {
      root = safeYaml().load(is);
    } catch (YAMLException e) {
      throw new PieceManifestValidationException(List.of(
              new ManifestReloadResponse.ErrorEntry("-", "yaml", e.getMessage())));
    } catch (IOException e) {
      throw new IllegalStateException(
              "failed to read manifest from " + path + ": " + e.getMessage(),
              e);
    }

    PieceManifest manifest = parseManifest(root);
    return Snapshot.of(manifest.pieces());
  }

  @SuppressWarnings("unchecked")
  private PieceManifest parseManifest(Map<String, Object> root) {
    if (root == null) {
      throw new PieceManifestValidationException(List.of(
              new ManifestReloadResponse.ErrorEntry("-", "root", "manifest document is empty")));
    }
    Object piecesRaw = root.get("pieces");
    if (!(piecesRaw instanceof List<?> list)) {
      throw new PieceManifestValidationException(List.of(
              new ManifestReloadResponse.ErrorEntry("-", "pieces",
                      "missing or non-array 'pieces' field")));
    }

    List<ManifestReloadResponse.ErrorEntry> errors = new ArrayList<>();
    List<Piece> pieces = new ArrayList<>();
    Set<String> seenIds = new HashSet<>();

    int index = 0;
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> raw)) {
        errors.add(new ManifestReloadResponse.ErrorEntry("#" + index, "piece",
                "piece entry must be an object"));
        index++;
        continue;
      }
      try {
        Piece piece = parsePiece((Map<String, Object>) raw, seenIds);
        if (seenIds.contains(piece.id())) {
          errors.add(new ManifestReloadResponse.ErrorEntry(piece.id(), "id",
                  "duplicate piece id '" + piece.id() + "'"));
        } else {
          seenIds.add(piece.id());
          pieces.add(piece);
        }
      } catch (PieceManifestValidationException e) {
        errors.addAll(e.errors());
      } catch (RuntimeException e) {
        errors.add(new ManifestReloadResponse.ErrorEntry("#" + index, "piece",
                e.getMessage() == null ? e.getClass().getName() : e.getMessage()));
      }
      index++;
    }

    if (!errors.isEmpty()) {
      throw new PieceManifestValidationException(errors);
    }
    return new PieceManifest(pieces);
  }

  @SuppressWarnings("unchecked")
  private Piece parsePiece(Map<String, Object> raw, Set<String> seenIds) {
    String id = requireString(raw, "id", "-");
    validateId(id);

    Object targetRaw = raw.get("target");
    if (!(targetRaw instanceof Map<?, ?>)) {
      throw entryError(id, "target", "target must be an object with a 'type' field");
    }
    Target target = parseTarget((Map<String, Object>) targetRaw, id);

    List<PreCheck> preChecks = parsePreChecks(raw.get("preCheck"), id);
    List<PostCheck> postChecks = parsePostChecks(raw.get("postCheck"), id);

    String failMode = raw.get("failMode") instanceof String s ? s : "deny";
    if (!VALID_FAIL_MODES.contains(failMode)) {
      throw entryError(id, "failMode",
              "unknown failMode '" + failMode + "'; expected 'deny' or 'audit-only'");
    }

    ObjectAllow allow = parseObjectAllow(raw.get("allow"), id);
    ObjectDeny deny = parseObjectDeny(raw.get("deny"), id);

    return new Piece(id, target, preChecks, postChecks, failMode, allow, deny);
  }

  private Target parseTarget(Map<String, Object> raw, String pieceId) {
    String type = requireString(raw, "type", pieceId);
    return switch (type) {
      case "api" -> {
        String route = requireString(raw, "route", pieceId);
        if (!ROUTE_PATTERN.matcher(route).matches()) {
          throw entryError(pieceId, "target.route",
                  "route must be 'METHOD path', e.g. 'POST /api/dsl/reload'");
        }
        yield new Target.ApiTarget(route);
      }
      case "button" -> new Target.ButtonTarget(requireString(raw, "uiKey", pieceId));
      case "object" -> {
        String objectType = requireString(raw, "objectType", pieceId);
        if (!VALID_OBJECT_TYPES.contains(objectType)) {
          throw entryError(pieceId, "target.objectType",
                  "unknown objectType '" + objectType + "'; expected helper, process, or function");
        }
        yield new Target.ObjectTarget(objectType, requireString(raw, "objectName", pieceId));
      }
      default -> throw entryError(pieceId, "target.type",
              "unknown target type '" + type + "'; expected api, button, or object");
    };
  }

  @SuppressWarnings("unchecked")
  private List<PreCheck> parsePreChecks(Object raw, String pieceId) {
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> list)) {
      throw entryError(pieceId, "preCheck", "preCheck must be an array");
    }
    List<PreCheck> checks = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> rawCheck)) {
        throw entryError(pieceId, "preCheck", "each preCheck entry must be an object");
      }
      Map<String, Object> map = (Map<String, Object>) rawCheck;
      String type = requireString(map, "type", pieceId);
      checks.add(switch (type) {
        case "role" -> {
          Object anyOfRaw = map.get("anyOf");
          if (!(anyOfRaw instanceof List<?> roles)) {
            throw entryError(pieceId, "preCheck.anyOf", "anyOf must be an array of role names");
          }
          List<String> roleNames = new ArrayList<>();
          for (Object role : roles) {
            if (!(role instanceof String rs)) {
              throw entryError(pieceId, "preCheck.anyOf", "role names must be strings");
            }
            String lower = rs.toLowerCase(Locale.ROOT);
            if (!VALID_ROLES.contains(lower)) {
              throw entryError(pieceId, "preCheck.anyOf",
                      "unknown role '" + rs + "'");
            }
            roleNames.add(lower);
          }
          if (roleNames.isEmpty()) {
            throw entryError(pieceId, "preCheck.anyOf", "anyOf must contain at least one role");
          }
          yield new PreCheck.RoleCheck(roleNames);
        }
        case "feature-flag" -> new PreCheck.FeatureFlagCheck(requireString(map, "flag", pieceId));
        case "rate-class" -> new PreCheck.RateClassCheck(requireString(map, "class", pieceId));
        default -> throw entryError(pieceId, "preCheck.type",
                "unknown preCheck type '" + type + "'; expected role, feature-flag, or rate-class");
      });
    }
    return checks;
  }

  @SuppressWarnings("unchecked")
  private List<PostCheck> parsePostChecks(Object raw, String pieceId) {
    if (raw == null) {
      return List.of();
    }
    if (!(raw instanceof List<?> list)) {
      throw entryError(pieceId, "postCheck", "postCheck must be an array");
    }
    List<PostCheck> checks = new ArrayList<>();
    for (Object item : list) {
      if (!(item instanceof Map<?, ?> rawCheck)) {
        throw entryError(pieceId, "postCheck", "each postCheck entry must be an object");
      }
      Map<String, Object> map = (Map<String, Object>) rawCheck;
      String type = requireString(map, "type", pieceId);
      checks.add(switch (type) {
        case "audit-write" -> new PostCheck.AuditWriteCheck(requireString(map, "action", pieceId),
                onFailureOf(map, pieceId));
        case "invariant-assert" -> new PostCheck.InvariantAssertCheck(
                map.get("expr") instanceof String s ? s : null,
                map.get("description") instanceof String s ? s : null,
                onFailureOf(map, pieceId));
        case "notify" -> new PostCheck.NotifyCheck(requireString(map, "channel", pieceId),
                onFailureOf(map, pieceId));
        default -> throw entryError(pieceId, "postCheck.type",
                "unknown postCheck type '" + type
                        + "'; expected audit-write, invariant-assert, or notify");
      });
    }
    return checks;
  }

  @SuppressWarnings("unchecked")
  private ObjectAllow parseObjectAllow(Object raw, String pieceId) {
    if (raw == null) {
      return null;
    }
    if (!(raw instanceof Map<?, ?>)) {
      throw entryError(pieceId, "allow", "allow must be an object");
    }
    Map<String, Object> map = (Map<String, Object>) raw;
    return new ObjectAllow(
            stringSet(map.get("definitions")),
            stringSet(map.get("helpers")),
            stringSet(map.get("capabilities")));
  }

  @SuppressWarnings("unchecked")
  private ObjectDeny parseObjectDeny(Object raw, String pieceId) {
    if (raw == null) {
      return null;
    }
    if (!(raw instanceof Map<?, ?>)) {
      throw entryError(pieceId, "deny", "deny must be an object");
    }
    Map<String, Object> map = (Map<String, Object>) raw;
    return new ObjectDeny(
            stringSet(map.get("definitions")),
            stringSet(map.get("helpers")),
            stringSet(map.get("capabilities")));
  }

  @SuppressWarnings("unchecked")
  private Set<String> stringSet(Object raw) {
    if (raw == null) {
      return Set.of();
    }
    if (!(raw instanceof List<?> list)) {
      return Set.of();
    }
    Set<String> result = new LinkedHashSet<>();
    for (Object item : list) {
      if (item instanceof String s && !s.isBlank()) {
        result.add(s);
      }
    }
    return result;
  }
  private String requireString(Map<String, Object> map, String key, String pieceId) {
    Object value = map.get(key);
    if (!(value instanceof String s) || s.isBlank()) {
      throw entryError(pieceId, key, "required string field '" + key + "' is missing or blank");
    }
    return s;
  }

  /**
   * Reads and validates a postCheck entry's {@code onFailure} policy; absent/blank defaults to
   * {@code warn} (T547 schema, enforced since T550).
   */
  private String onFailureOf(Map<String, Object> map, String pieceId) {
    String onFailure = map.get("onFailure") instanceof String s ? s.trim() : null;
    if (onFailure == null || onFailure.isBlank()) {
      return PostCheck.ON_FAILURE_WARN;
    }
    String normalized = onFailure.toLowerCase(Locale.ROOT);
    if (!VALID_ON_FAILURE.contains(normalized)) {
      throw entryError(pieceId, "postCheck.onFailure",
              "unknown onFailure '" + onFailure + "'; expected 'warn' or 'block-next-execution'");
    }
    return normalized;
  }

  private void validateId(String id) {
    if (!ID_PATTERN.matcher(id).matches()) {
      throw entryError(id, "id",
              "id must be kebab-case lowercase letters/digits/hyphens, e.g. 'dsl-reload'");
    }
  }

  private static PieceManifestValidationException entryError(String pieceId, String field,
          String message) {
    return new PieceManifestValidationException(List.of(
            new ManifestReloadResponse.ErrorEntry(pieceId, field, message)));
  }

  private void audit(ServerRequest request, String target, String outcome, Object details) {
    if (auditServiceProvider == null) {
      return;
    }
    var auditService = auditServiceProvider.getIfAvailable();
    if (auditService == null) {
      return;
    }
    auditService.record(DslAuditService.currentActor(), ACTION_MANIFEST_RELOAD, target,
            DslAuditService.correlationIdOf(request), outcome, details);
  }

  private static ServerResponse error(HttpStatus status, String code, String message) {
    return ServerResponse.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(Map.of("code", code, "message", message));
  }

  private static Yaml safeYaml() {
    LoaderOptions options = new LoaderOptions();
    options.setAllowDuplicateKeys(false);
    options.setMaxAliasesForCollections(50);
    options.setCodePointLimit(cbs.nova.starter.core.StarterConstants.YAML_MAX_CODE_POINTS);
    options.setTagInspector(tag -> false);
    return new Yaml(new SafeConstructor(options));
  }

  private record Route(String method, String path) {

    static Route parse(String route) {
      int space = route.indexOf(' ');
      if (space < 0) {
        throw new IllegalArgumentException("invalid route: " + route);
      }
      return new Route(route.substring(0, space), route.substring(space + 1));
    }
  }

  /**
   * Immutable snapshot with pre-built indexes. Rebuilt on every load and atomically swapped.
   */
  static final class Snapshot {

    final List<Piece> pieces;
    final Map<String, Piece> byId;
    final Map<String, List<Piece>> byTargetType;
    final List<Piece> apiPieces;
    final List<Piece> objectPieces;

    private Snapshot(List<Piece> pieces, Map<String, Piece> byId,
            Map<String, List<Piece>> byTargetType, List<Piece> apiPieces,
            List<Piece> objectPieces) {
      this.pieces = pieces;
      this.byId = byId;
      this.byTargetType = byTargetType;
      this.apiPieces = apiPieces;
      this.objectPieces = objectPieces;
    }

    static Snapshot empty() {
      return new Snapshot(List.of(), Map.of(), Map.of(), List.of(), List.of());
    }

    static Snapshot of(List<Piece> pieces) {
      Map<String, Piece> byId = new LinkedHashMap<>();
      Map<String, List<Piece>> byTargetType = new LinkedHashMap<>();
      List<Piece> apiPieces = new ArrayList<>();
      List<Piece> objectPieces = new ArrayList<>();
      for (Piece piece : pieces) {
        byId.put(piece.id(), piece);
        byTargetType.computeIfAbsent(piece.target().type(), k -> new ArrayList<>()).add(piece);
        if (piece.target() instanceof Target.ApiTarget) {
          apiPieces.add(piece);
        }
        if (piece.target() instanceof Target.ObjectTarget) {
          objectPieces.add(piece);
        }
      }
      return new Snapshot(List.copyOf(pieces), Collections.unmodifiableMap(byId),
              Collections.unmodifiableMap(byTargetType), List.copyOf(apiPieces),
              List.copyOf(objectPieces));
    }
  }
}
