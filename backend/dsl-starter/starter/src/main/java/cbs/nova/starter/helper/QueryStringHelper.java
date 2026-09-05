package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.QueryStringIn;
import cbs.nova.starter.helper.model.QueryStringOut;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;

/**
 * Builds and parses application/x-www-form-urlencoded query strings.
 *
 * <p>
 * The helper supports two modes (case-insensitive):
 * <ul>
 * <li>{@code "build"}: form-encodes {@code params} (Map&lt;String, String&gt;) into a single
 * {@code key=value&...} string in the map's iteration order. Spaces become {@code "+"} via
 * {@link URLEncoder}. Null params, null keys, or null values yield an
 * {@link IllegalArgumentException}; an empty map yields an empty string.</li>
 * <li>{@code "parse"}: percent-decodes {@code queryString} into an ordered {@code LinkedHashMap} of
 * entries. An optional leading {@code "?"} is stripped, then the body is split on {@code "&"}. Each
 * segment containing {@code "="} is split on the first {@code "="} and both halves are
 * percent-decoded via {@link URLDecoder}; segments without {@code "="} are skipped with a warning
 * log. Null or empty input yields an empty map.</li>
 * </ul>
 *
 * <p>
 * Unknown or null {@code mode} yields an {@link IllegalArgumentException}.
 */
@Slf4j
@Helper(name = "queryString")
public class QueryStringHelper implements Executable<QueryStringIn, QueryStringOut> {

  @Override
  public @NonNull Result<QueryStringOut> execute(@NonNull Context<QueryStringIn> ctx) {
    try {
      QueryStringIn input = ctx.body();
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "build" -> build(input.params());
        case "parse" -> parse(input.queryString());
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "queryString.mode must be one of build, parse, was: " + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static Result<QueryStringOut> build(Map<String, String> params) {
    if (params == null) {
      return Result.failure(new IllegalArgumentException("queryString.params is required"));
    }
    if (params.isEmpty()) {
      return Result.success(new QueryStringOut(""));
    }
    params.forEach((key, value) -> {
      if (key == null) {
        throw new IllegalArgumentException("queryString.params contains a null key");
      }
      if (value == null) {
        throw new IllegalArgumentException("queryString.params contains a null value for key '"
                + key + "'");
      }
    });
    String joined = params.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8)
                    + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    return Result.success(new QueryStringOut(joined));
  }

  private static Result<QueryStringOut> parse(String queryString) {
    if (queryString == null) {
      return Result.failure(
              new IllegalArgumentException("queryString.queryString is required"));
    }
    Map<String, String> result = new LinkedHashMap<>();
    if (queryString.isEmpty()) {
      return Result.success(new QueryStringOut(result));
    }
    String body = queryString.startsWith("?") ? queryString.substring(1) : queryString;
    if (body.isEmpty()) {
      return Result.success(new QueryStringOut(result));
    }
    for (String segment : body.split("&")) {
      int eq = segment.indexOf('=');
      if (eq < 0) {
        log.warn("queryString.parse: skipping malformed segment '{}'", segment);
        continue;
      }
      String key = URLDecoder.decode(segment.substring(0, eq), StandardCharsets.UTF_8);
      String value = URLDecoder.decode(segment.substring(eq + 1), StandardCharsets.UTF_8);
      result.put(key, value);
    }
    return Result.success(new QueryStringOut(result));
  }
}
