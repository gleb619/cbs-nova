package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.Base64In;
import cbs.nova.starter.helper.model.Base64Out;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import org.jspecify.annotations.NonNull;

/**
 * Encodes and decodes strings using standard or URL-safe Base64.
 *
 * <p>
 * The helper supports two modes:
 * <ul>
 * <li>{@code "encode"}: converts the UTF-8 representation of {@code input} to Base64.</li>
 * <li>{@code "decode"}: converts a Base64 string back to a UTF-8 string.</li>
 * </ul>
 *
 * <p>
 * When {@code urlSafe} is {@code true}, the URL-safe alphabet {@code -_} is used and padding is
 * retained. A null or blank input encodes/decodes to an empty string; a null input is rejected with
 * an {@link IllegalArgumentException}.
 */
@Helper(name = "base64")
public class Base64Helper implements Executable<Base64In, Base64Out> {

  @Override
  public @NonNull Result<Base64Out> execute(@NonNull Context<Base64In> ctx) {
    try {
      Base64In input = ctx.body();
      if (input.input() == null) {
        return Result.failure(new IllegalArgumentException("base64.input is required"));
      }
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      boolean urlSafe = input.urlSafe() != null && input.urlSafe();
      return switch (mode) {
        case "encode" -> Result.success(new Base64Out(encode(input.input(), urlSafe)));
        case "decode" -> decode(input.input(), urlSafe);
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "base64.mode must be 'encode' or 'decode', was: " + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static String encode(String input, boolean urlSafe) {
    byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
    Base64.Encoder encoder = urlSafe ? Base64.getUrlEncoder() : Base64.getEncoder();
    return encoder.encodeToString(bytes);
  }

  private static Result<Base64Out> decode(String input, boolean urlSafe) {
    try {
      Base64.Decoder decoder = urlSafe ? Base64.getUrlDecoder() : Base64.getDecoder();
      byte[] bytes = decoder.decode(input.isBlank() ? "" : input);
      return Result.success(new Base64Out(new String(bytes, StandardCharsets.UTF_8)));
    } catch (IllegalArgumentException e) {
      return Result.failure(new IllegalArgumentException("base64.decode: invalid base64 input", e));
    }
  }
}
