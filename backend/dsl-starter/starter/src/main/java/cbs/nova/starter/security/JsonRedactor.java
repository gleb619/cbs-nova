package cbs.nova.starter.security;

import cbs.nova.starter.config.properties.CbsDslRedactionProperties;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;
import tools.jackson.databind.node.StringNode;

/**
 * Size-bounded, fail-safe JSON redactor for run data at egress.
 *
 * <p>
 * Walks a Jackson tree and replaces values whose field name matches any configured case-
 * insensitive regex with a fixed replacement string. Matching is applied at every object depth;
 * arrays are recursed into. String values that themselves contain JSON are parsed and redacted
 * recursively so that sensitive values embedded in serialized JSON strings (for example a domain
 * event {@code outputJson} field) are also masked.
 *
 * <p>
 * The redactor is deliberately safe:
 * <ul>
 * <li>When redaction is disabled, the current caller has a bypass role, the input is larger than
 * {@code maxInputLength}, or the input is malformed, the original value is returned unchanged.</li>
 * <li>No exception is thrown from any public method.</li>
 * </ul>
 */
public final class JsonRedactor {

  private static final int DEFAULT_MAX_INPUT_LENGTH = 10_000_000;

  private final ObjectMapper objectMapper;
  private final boolean enabled;
  private final List<Pattern> keyPatterns;
  private final String replacement;
  private final Set<String> bypassRoles;
  private final int maxInputLength;

  public JsonRedactor(ObjectMapper objectMapper, CbsDslRedactionProperties properties) {
    this.objectMapper = objectMapper;
    this.enabled = properties.enabled();
    this.keyPatterns = compile(properties.keys());
    this.replacement = properties.replacement();
    this.bypassRoles = properties.bypassRoles().stream()
            .map(String::toUpperCase)
            .collect(Collectors.toUnmodifiableSet());
    this.maxInputLength = properties.maxInputLength() > 0
            ? properties.maxInputLength()
            : DEFAULT_MAX_INPUT_LENGTH;
  }

  private JsonRedactor() {
    this.objectMapper = new ObjectMapper();
    this.enabled = false;
    this.keyPatterns = List.of();
    this.replacement = "***";
    this.bypassRoles = Set.of();
    this.maxInputLength = DEFAULT_MAX_INPUT_LENGTH;
  }

  /**
   * Returns a redactor that never changes its input. Useful for tests and for constructors that
   * want a default no-op collaborator.
   */
  public static JsonRedactor disabled() {
    return new JsonRedactor();
  }

  /**
   * Redacts a JSON string. Returns the original string when redaction is disabled, the caller is
   * exempt, the input is oversized, or the input is not valid JSON.
   */
  public @Nullable String redact(@Nullable String json) {
    if (json == null || !enabled || isBypassed() || json.length() > maxInputLength) {
      return json;
    }
    try {
      JsonNode root = objectMapper.readTree(json);
      JsonNode redacted = redact(root);
      return objectMapper.writeValueAsString(redacted);
    } catch (Exception ignored) {
      return json;
    }
  }

  /**
   * Redacts a Jackson {@link JsonNode}. Returns the original node when redaction is disabled, the
   * caller is exempt, or redaction fails.
   */
  public @Nullable JsonNode redact(@Nullable JsonNode node) {
    if (node == null || !enabled || isBypassed()) {
      return node;
    }
    try {
      return doRedact(node);
    } catch (Exception ignored) {
      return node;
    }
  }

  /**
   * Redacts an arbitrary Java object by converting it to a Jackson tree first. Returns the original
   * object when redaction is disabled, the caller is exempt, or redaction fails.
   */
  public @Nullable Object redactValue(@Nullable Object value) {
    if (value == null || !enabled || isBypassed()) {
      return value;
    }
    try {
      JsonNode node = objectMapper.valueToTree(value);
      JsonNode redacted = redact(node);
      return redacted;
    } catch (Exception ignored) {
      return value;
    }
  }

  private JsonNode doRedact(JsonNode node) {
    if (node.isObject()) {
      ObjectNode out = objectMapper.createObjectNode();
      node.properties().forEach(entry -> {
        String key = entry.getKey();
        JsonNode value = entry.getValue();
        if (shouldMask(key)) {
          out.set(key, StringNode.valueOf(replacement));
        } else {
          out.set(key, doRedact(value));
        }
      });
      return out;
    }
    if (node.isArray()) {
      ArrayNode out = objectMapper.createArrayNode();
      ((ArrayNode) node).elements().forEach(element -> out.add(doRedact(element)));
      return out;
    }
    if (node.isTextual()) {
      String text = node.textValue();
      if (looksLikeJson(text)) {
        try {
          JsonNode parsed = objectMapper.readTree(text);
          JsonNode redacted = doRedact(parsed);
          return StringNode.valueOf(objectMapper.writeValueAsString(redacted));
        } catch (Exception ignored) {
          // Not actually JSON, keep the original string.
        }
      }
      return node;
    }
    return node;
  }

  private boolean shouldMask(String key) {
    for (Pattern pattern : keyPatterns) {
      if (pattern.matcher(key).find()) {
        return true;
      }
    }
    return false;
  }

  private boolean looksLikeJson(String value) {
    if (value == null || value.length() < 2) {
      return false;
    }
    String trimmed = value.trim();
    return (trimmed.startsWith("{") && trimmed.endsWith("}"))
            || (trimmed.startsWith("[") && trimmed.endsWith("]"));
  }

  private boolean isBypassed() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth == null || !auth.isAuthenticated()) {
      return false;
    }
    Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
    if (authorities == null) {
      return false;
    }
    for (GrantedAuthority authority : authorities) {
      String name = authority.getAuthority();
      if (name == null || name.isBlank()) {
        continue;
      }
      String upper = name.toUpperCase();
      if (bypassRoles.contains(upper)) {
        return true;
      }
      if (upper.startsWith("ROLE_")) {
        if (bypassRoles.contains(upper.substring(5))) {
          return true;
        }
      } else if (bypassRoles.contains("ROLE_" + upper)) {
        return true;
      }
    }
    return false;
  }

  private static List<Pattern> compile(List<String> patterns) {
    List<Pattern> out = new ArrayList<>(patterns.size());
    for (String raw : patterns) {
      if (raw != null && !raw.isBlank()) {
        out.add(Pattern.compile(raw, Pattern.CASE_INSENSITIVE));
      }
    }
    return List.copyOf(out);
  }
}
