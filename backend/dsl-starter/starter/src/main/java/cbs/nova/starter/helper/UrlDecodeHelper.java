package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.UrlDecodeIn;
import cbs.nova.starter.helper.model.UrlDecodeOut;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import org.jspecify.annotations.NonNull;

/**
 * Percent-decodes a string produced by {@link UrlEncodeHelper} or another RFC 3986 / form encoder.
 *
 * <p>
 * Charset defaults to {@code UTF-8} when null or blank. {@code form} defaults to {@code false};
 * when {@code true} {@code +} is decoded to a space for {@code application/x-www-form-urlencoded}
 * bodies, otherwise a literal {@code +} is preserved.
 */
@Helper(name = "urlDecode")
public class UrlDecodeHelper implements Executable<UrlDecodeIn, UrlDecodeOut> {

  @Override
  public @NonNull Result<UrlDecodeOut> execute(@NonNull Context<UrlDecodeIn> ctx) {
    try {
      UrlDecodeIn input = ctx.body();
      if (input.input() == null || input.input().isEmpty()) {
        return Result.failure(new IllegalArgumentException("urlDecode.input is required"));
      }
      Charset charset = resolveCharset(input.charset());
      boolean form = input.form() != null && input.form();
      return Result.success(new UrlDecodeOut(decode(input.input(), charset, form)));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static String decode(String input, Charset charset, boolean form) {
    try {
      return form
              ? URLDecoder.decode(input, charset)
              : URLDecoder.decode(input.replace("+", "%2B"), charset);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("urlDecode: invalid percent-encoded input", e);
    }
  }

  private static Charset resolveCharset(String charsetName) {
    if (charsetName == null || charsetName.isBlank()) {
      return StandardCharsets.UTF_8;
    }
    try {
      return Charset.forName(charsetName);
    } catch (IllegalCharsetNameException | UnsupportedCharsetException e) {
      throw new IllegalArgumentException("urlDecode.charset is invalid: " + charsetName, e);
    }
  }
}
