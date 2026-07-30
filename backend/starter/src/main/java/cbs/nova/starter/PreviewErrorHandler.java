package cbs.nova.starter;

import cbs.nova.dsl.DslCompensationException;
import cbs.nova.dsl.DslEntityNotFoundException;
import cbs.nova.dsl.DslValidationException;
import cbs.nova.dsl.PreviewErrorCode;
import cbs.nova.dsl.PreviewErrorDetail;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Maps exceptions raised during preview or explain dispatch to a developer-friendly
 * {@link PreviewErrorDetail} carrying an error code, message, recovery suggestion, and
 * JSON-serializable context map.
 */
@UtilityClass
public class PreviewErrorHandler {

  private static final String UNKNOWN_ENTITY_PREFIX = "No DSL entity registered: ";
  private static final String HELPER_NOT_FOUND_PREFIX = "Helper not found: ";

  /**
   * Map an exception to a {@link PreviewErrorDetail} using only the throwable as context.
   */
  public @NonNull PreviewErrorDetail from(@Nullable Throwable cause) {
    return from(cause, null);
  }

  /**
   * Map an exception to a {@link PreviewErrorDetail}. When {@code entityName} is non-null it is
   * always added to the context map under {@code name} so callers can correlate the failure with
   * the DSL entity that was being dispatched.
   */
  public @NonNull PreviewErrorDetail from(@Nullable Throwable cause,
          @Nullable String entityName) {
    if (cause == null) {
      return build(PreviewErrorCode.UNKNOWN_ERROR, "Preview failed with no cause", Map.of(),
              entityName);
    }

    if (cause instanceof NoSuchBeanDefinitionException nsbe) {
      String beanName = extractBeanName(nsbe.getBeanName());
      return helperNotFound(messageOf(cause), beanName, entityName != null ? entityName : beanName);
    }

    if (cause instanceof SQLException sql) {
      return externalCallFailed("SQL error: " + messageOf(sql), sql, entityName);
    }

    if (cause instanceof ClassCastException cce) {
      return inputValidationError(messageOf(cce), cce, entityName);
    }

    if (cause instanceof TimeoutException) {
      return timeoutExceeded(messageOf(cause), entityName);
    }

    if (cause instanceof DslValidationException dve) {
      return build(PreviewErrorCode.DSL_COMPILATION_ERROR, messageOf(dve),
              Map.of("runId", dve.runId()), entityName);
    }

    if (cause instanceof DslCompensationException dce) {
      return build(PreviewErrorCode.COMPENSATION_ERROR,
              "Compensation failed: " + messageOf(dce),
              Map.of("runId", dce.runId()), entityName);
    }

    if (cause instanceof DslEntityNotFoundException dene) {
      String msg = messageOf(dene);
      if (msg != null && msg.startsWith(HELPER_NOT_FOUND_PREFIX)) {
        return helperNotFound(msg, extractAfterPrefix(msg, HELPER_NOT_FOUND_PREFIX), entityName);
      }
      return build(PreviewErrorCode.UNKNOWN_ERROR, msg, Map.of("runId", dene.runId()), entityName);
    }

    if (cause instanceof IllegalArgumentException iae) {
      String msg = messageOf(iae);
      if (msg != null && msg.startsWith(UNKNOWN_ENTITY_PREFIX)) {
        return helperNotFound(msg, extractAfterPrefix(msg, UNKNOWN_ENTITY_PREFIX), entityName);
      }
      return build(PreviewErrorCode.INPUT_VALIDATION_ERROR, msg, Map.of(), entityName);
    }

    if (cause instanceof RuntimeException re) {
      return build(PreviewErrorCode.UNKNOWN_ERROR, messageOf(re), Map.of("exceptionType",
              re.getClass().getName()), entityName);
    }

    return build(PreviewErrorCode.UNKNOWN_ERROR, messageOf(cause),
            Map.of("exceptionType", cause.getClass().getName()), entityName);
  }

  private @NonNull PreviewErrorDetail helperNotFound(@NonNull String message,
          @Nullable String helperName, @Nullable String entityName) {
    Map<String, Object> ctx = new HashMap<>();
    String name = helperName != null && !helperName.isBlank()
            ? helperName
            : entityName;
    if (name != null && !name.isBlank()) {
      ctx.put("name", name);
    }
    return new PreviewErrorDetail(PreviewErrorCode.HELPER_NOT_FOUND, message,
            "Register the helper class with @Helper or ensure it is on the classpath; check helper registration.",
            ctx);
  }

  private @NonNull PreviewErrorDetail build(@NonNull PreviewErrorCode code,
          @Nullable String message, @NonNull Map<String, Object> ctx, @Nullable String entityName) {
    String msg = message != null ? message : code.name();
    Map<String, Object> merged = new HashMap<>(ctx);
    if (entityName != null && !entityName.isBlank() && !merged.containsKey("name")) {
      merged.put("name", entityName);
    }
    return new PreviewErrorDetail(code, msg, defaultSuggestion(code), merged);
  }

  private @NonNull PreviewErrorDetail externalCallFailed(@NonNull String message,
          @NonNull SQLException sql, @Nullable String entityName) {
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("sqlState", sql.getSQLState());
    ctx.put("errorCode", sql.getErrorCode());
    if (sql.getMessage() != null) {
      ctx.put("sql", sql.getMessage());
    }
    if (entityName != null && !entityName.isBlank()) {
      ctx.put("name", entityName);
    }
    return new PreviewErrorDetail(PreviewErrorCode.EXTERNAL_CALL_FAILED, message,
            "Inspect the SQL query, table, and database connectivity; verify the DataSource is reachable.",
            ctx);
  }

  private @NonNull PreviewErrorDetail inputValidationError(@NonNull String message,
          @NonNull ClassCastException cce, @Nullable String entityName) {
    Map<String, Object> ctx = new HashMap<>();
    ctx.put("exceptionType", cce.getClass().getName());
    if (entityName != null && !entityName.isBlank()) {
      ctx.put("name", entityName);
    }
    return new PreviewErrorDetail(PreviewErrorCode.INPUT_VALIDATION_ERROR, message,
            "Verify the input matches the expected schema; check the type and required fields.",
            ctx);
  }

  private @NonNull PreviewErrorDetail timeoutExceeded(@Nullable String message,
          @Nullable String entityName) {
    String msg = message != null ? message : "Preview execution exceeded the allowed timeout";
    Map<String, Object> ctx = new HashMap<>();
    if (entityName != null && !entityName.isBlank()) {
      ctx.put("name", entityName);
    }
    return new PreviewErrorDetail(PreviewErrorCode.TIMEOUT_EXCEEDED, msg,
            "Increase the preview timeout or simplify the DSL to reduce execution time.", ctx);
  }

  private @NonNull String defaultSuggestion(@NonNull PreviewErrorCode code) {
    return switch (code) {
      case DSL_COMPILATION_ERROR ->
        "Check the DSL source for syntax or validation errors; review the referenced entity names.";
      case HELPER_NOT_FOUND ->
        "Register the helper class with @Helper or ensure it is on the classpath.";
      case EXTERNAL_CALL_FAILED ->
        "Inspect the external call target (URL/JDBC URL) and connectivity; review credentials.";
      case INPUT_VALIDATION_ERROR ->
        "Verify the input matches the expected schema; check the type and required fields.";
      case COMPENSATION_ERROR ->
        "Review compensation logic for the failing transaction; ensure it is idempotent.";
      case TIMEOUT_EXCEEDED ->
        "Increase the preview timeout or simplify the DSL to reduce execution time.";
      case UNKNOWN_ERROR ->
        "Review the stack trace and recent changes; report if the issue persists.";
    };
  }

  private @Nullable String messageOf(@Nullable Throwable t) {
    return t == null ? null : t.getMessage();
  }

  private @Nullable String extractBeanName(@Nullable String beanName) {
    return beanName != null && !beanName.isBlank() ? beanName : null;
  }

  private @Nullable String extractAfterPrefix(@NonNull String message,
          @NonNull String prefix) {
    String tail = message.substring(prefix.length()).trim();
    if (tail.isEmpty()) {
      return null;
    }
    int newline = tail.indexOf('\n');
    if (newline >= 0) {
      tail = tail.substring(0, newline).trim();
    }
    return tail.isEmpty() ? null : tail;
  }
}
