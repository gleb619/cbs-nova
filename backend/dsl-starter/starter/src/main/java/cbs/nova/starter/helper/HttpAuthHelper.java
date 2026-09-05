package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.HttpAuthIn;
import cbs.nova.starter.helper.model.HttpAuthOut;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.jspecify.annotations.NonNull;

/**
 * Builds HTTP authentication header maps.
 *
 * <p>
 * The helper supports four modes (case-insensitive):
 * <ul>
 * <li>{@code "bearer"}: returns {@code {"Authorization": "Bearer <token>"}}. Requires a non-blank
 * {@code token}; internal whitespace is preserved as-is per RFC 6750 §2.1.</li>
 * <li>{@code "basic"}: returns {@code {"Authorization": "Basic <base64>"}} where the base64 is the
 * standard-alphabet (with padding, not URL-safe) encoding of the UTF-8 bytes of
 * {@code "username:password"}. {@code username} must be non-blank; {@code password} may be
 * blank/empty.</li>
 * <li>{@code "apiKey"}: returns {@code {<header>: <value>}}. {@code key} must be non-blank.
 * {@code header} defaults to {@code "X-Api-Key"} when {@code null}; an explicit blank string
 * {@code ""} is rejected. {@code prefix} defaults to {@code ""}; when non-blank the value is
 * {@code "<prefix> <key>"}.</li>
 * <li>{@code "custom"}: returns {@code {<header>: <value>}}. Both {@code header} and {@code value}
 * are required: {@code header} must be non-blank, {@code value} must be non-null (blank {@code ""}
 * is allowed).</li>
 * </ul>
 *
 * <p>
 * Unknown or {@code null} {@code mode}, or any missing/invalid required argument, yields an
 * {@link IllegalArgumentException} describing the problem.
 */
@Helper(name = "httpAuth")
public class HttpAuthHelper implements Executable<HttpAuthIn, HttpAuthOut> {

  private static final String DEFAULT_API_KEY_HEADER = "X-Api-Key";

  @Override
  public @NonNull Result<HttpAuthOut> execute(@NonNull Context<HttpAuthIn> ctx) {
    try {
      HttpAuthIn input = ctx.body();
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "bearer" -> bearer(input.token());
        case "basic" -> basic(input.username(), input.password());
        case "apikey" -> apiKey(input.key(), input.header(), input.prefix());
        case "custom" -> custom(input.header(), input.value());
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "httpAuth.mode must be one of bearer, basic, apiKey, custom, was: "
                                + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static @NonNull Result<HttpAuthOut> bearer(String token) {
    if (token == null || token.isBlank()) {
      throw new IllegalArgumentException("httpAuth.bearer: token is required");
    }
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Authorization", "Bearer " + token);
    return Result.success(new HttpAuthOut(headers));
  }

  private static @NonNull Result<HttpAuthOut> basic(String username, String password) {
    if (username == null || username.isBlank()) {
      throw new IllegalArgumentException("httpAuth.basic: username is required");
    }
    String passwordPart = (password == null) ? "" : password;
    String credential = username + ":" + passwordPart;
    String encoded = Base64.getEncoder()
            .encodeToString(credential.getBytes(StandardCharsets.UTF_8));
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put("Authorization", "Basic " + encoded);
    return Result.success(new HttpAuthOut(headers));
  }

  private static @NonNull Result<HttpAuthOut> apiKey(String key, String header, String prefix) {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("httpAuth.apiKey: key is required");
    }
    String effectiveHeader = (header == null) ? DEFAULT_API_KEY_HEADER : header;
    if (effectiveHeader.isBlank()) {
      throw new IllegalArgumentException("httpAuth.apiKey: header is required");
    }
    String effectivePrefix = (prefix == null) ? "" : prefix;
    String value = effectivePrefix.isEmpty() ? key : effectivePrefix + " " + key;
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(effectiveHeader, value);
    return Result.success(new HttpAuthOut(headers));
  }

  private static @NonNull Result<HttpAuthOut> custom(String header, String value) {
    if (header == null || header.isBlank()) {
      throw new IllegalArgumentException("httpAuth.custom: header is required");
    }
    if (value == null) {
      throw new IllegalArgumentException("httpAuth.custom: value is required");
    }
    Map<String, String> headers = new LinkedHashMap<>();
    headers.put(header, value);
    return Result.success(new HttpAuthOut(headers));
  }
}
