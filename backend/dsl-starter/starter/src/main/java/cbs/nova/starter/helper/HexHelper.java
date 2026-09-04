package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.HexIn;
import cbs.nova.starter.helper.model.HexOut;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.Locale;
import org.jspecify.annotations.NonNull;

/**
 * Encodes and decodes strings as lowercase hexadecimal.
 *
 * <p>
 * The helper supports two modes:
 * <ul>
 * <li>{@code "encode"}: returns the lowercase hex of the UTF-8 bytes of {@code input}.</li>
 * <li>{@code "decode"}: parses a hex string and returns the UTF-8 string of those bytes.</li>
 * </ul>
 *
 * <p>
 * Empty input is rejected with an {@link IllegalArgumentException} in both modes, as is an
 * odd-length hex string in {@code "decode"} mode or any non-hex character. The {@code "mode"}
 * argument is case-insensitive.
 */
@Helper(name = "hex")
public class HexHelper implements Executable<HexIn, HexOut> {

  @Override
  public @NonNull Result<HexOut> execute(@NonNull Context<HexIn> ctx) {
    try {
      HexIn input = ctx.body();
      if (input.input() == null || input.input().isEmpty()) {
        return Result.failure(new IllegalArgumentException("hex.input is required"));
      }
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      return switch (mode) {
        case "encode" -> Result.success(new HexOut(encode(input.input())));
        case "decode" -> decode(input.input());
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "hex.mode must be 'encode' or 'decode', was: " + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static String encode(String input) {
    byte[] bytes = input.getBytes(StandardCharsets.UTF_8);
    return HexFormat.of().formatHex(bytes);
  }

  private static Result<HexOut> decode(String input) {
    try {
      byte[] bytes = HexFormat.of().parseHex(input);
      return Result.success(new HexOut(new String(bytes, StandardCharsets.UTF_8)));
    } catch (IllegalArgumentException e) {
      return Result.failure(new IllegalArgumentException("hex.decode: invalid hex input", e));
    }
  }
}
