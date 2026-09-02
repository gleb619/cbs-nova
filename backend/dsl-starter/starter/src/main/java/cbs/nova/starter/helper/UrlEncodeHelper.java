package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.UrlEncodeIn;
import cbs.nova.starter.helper.model.UrlEncodeOut;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import org.jspecify.annotations.NonNull;

/**
 * Percent-encodes a string for use in URLs or form payloads.
 *
 * <p>
 * Charset defaults to {@code UTF-8} when null or blank. {@code form} defaults to {@code false};
 * when {@code true} spaces are encoded as {@code +} for {@code application/x-www-form-urlencoded}
 * bodies, otherwise spaces are encoded as {@code %20} per RFC 3986.
 */
@Helper(name = "urlEncode")
public class UrlEncodeHelper implements Executable<UrlEncodeIn, UrlEncodeOut> {

  @Override
  public @NonNull Result<UrlEncodeOut> execute(@NonNull Context<UrlEncodeIn> ctx) {
    try {
      UrlEncodeIn input = ctx.body();
      if (input.input() == null || input.input().isEmpty()) {
        return Result.failure(new IllegalArgumentException("urlEncode.input is required"));
      }
      Charset charset = resolveCharset(input.charset());
      boolean form = input.form() != null && input.form();
      String encoded = form
              ? URLEncoder.encode(input.input(), charset)
              : URLEncoder.encode(input.input(), charset).replace("+", "%20");
      return Result.success(new UrlEncodeOut(encoded));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static Charset resolveCharset(String charsetName) {
    if (charsetName == null || charsetName.isBlank()) {
      return StandardCharsets.UTF_8;
    }
    try {
      return Charset.forName(charsetName);
    } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
      throw new IllegalArgumentException("urlEncode.charset is invalid: " + charsetName, e);
    }
  }
}
