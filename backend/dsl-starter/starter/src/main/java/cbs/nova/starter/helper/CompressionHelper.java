package cbs.nova.starter.helper;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.CompressionIn;
import cbs.nova.starter.helper.model.CompressionOut;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;
import org.jspecify.annotations.NonNull;

/**
 * Compresses and decompresses UTF-8 strings using gzip or raw DEFLATE.
 *
 * <p>
 * The helper supports four modes:
 * <ul>
 * <li>{@code "gzip"}: gzip-compresses the UTF-8 bytes of {@code input} and returns the result
 * base64-encoded.</li>
 * <li>{@code "gunzip"}: base64-decodes {@code input}, gzip-decompresses it, and returns the UTF-8
 * string of the recovered bytes.</li>
 * <li>{@code "deflate"}: zlib-wrapped DEFLATE-compresses the UTF-8 bytes of {@code input} and
 * returns the result base64-encoded.</li>
 * <li>{@code "inflate"}: base64-decodes {@code input}, zlib-wrapped DEFLATE-decompresses it, and
 * returns the UTF-8 string of the recovered bytes.</li>
 * </ul>
 *
 * <p>
 * The {@code level} argument controls the deflate compression level (0-9) for {@code "gzip"} and
 * {@code "deflate"}; it defaults to {@code -1} ({@link Deflater#DEFAULT_COMPRESSION}) when null.
 * Levels outside {@code [-1, 9]} are rejected with an {@link IllegalArgumentException}. A null or
 * blank {@code input} is rejected with an {@link IllegalArgumentException}. The {@code mode}
 * argument is case-insensitive.
 *
 * <p>
 * Malformed base64 or compressed payloads are reported as {@link IllegalArgumentException} via
 * {@link Result#failure(Throwable)}.
 */
@Helper(name = "compression")
public class CompressionHelper implements Executable<CompressionIn, CompressionOut> {

  @Override
  public @NonNull Result<CompressionOut> execute(@NonNull Context<CompressionIn> ctx) {
    try {
      CompressionIn input = ctx.body();
      if (input.input() == null || input.input().isEmpty()) {
        return Result.failure(new IllegalArgumentException("compression.input is required"));
      }
      String mode = (input.mode() == null) ? null : input.mode().toLowerCase(Locale.ROOT);
      int level = input.level() == null ? Deflater.DEFAULT_COMPRESSION : input.level();
      return switch (mode) {
        case "gzip" -> Result.success(new CompressionOut(gzip(input.input(), level)));
        case "gunzip" -> Result.success(new CompressionOut(gunzip(input.input())));
        case "deflate" -> Result.success(new CompressionOut(deflate(input.input(), level)));
        case "inflate" -> Result.success(new CompressionOut(inflate(input.input())));
        case null, default -> Result.failure(
                new IllegalArgumentException(
                        "compression.mode must be one of gzip, gunzip, deflate, inflate, was: "
                                + input.mode()));
      };
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }

  private static String gzip(String input, int level) {
    validateLevel(level);
    byte[] plain = input.getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    try (GZIPOutputStream gzip = new GZIPOutputStream(buffer) {
      {
        def.setLevel(level);
      }
    }) {
      gzip.write(plain);
    } catch (IOException e) {
      throw new IllegalStateException("compression.gzip: failed to write gzip stream", e);
    }
    return Base64.getEncoder().encodeToString(buffer.toByteArray());
  }

  private static String gunzip(String input) {
    byte[] compressed = decodeBase64(input, "gunzip");
    try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(compressed))) {
      return new String(readAllBytes(gzip), StandardCharsets.UTF_8);
    } catch (ZipException e) {
      throw new IllegalArgumentException("compression.gunzip: invalid gzip payload", e);
    } catch (IOException e) {
      throw new IllegalArgumentException("compression.gunzip: failed to read gzip stream", e);
    }
  }

  private static String deflate(String input, int level) {
    validateLevel(level);
    byte[] plain = input.getBytes(StandardCharsets.UTF_8);
    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    Deflater deflater = new Deflater(level, /* nowrap= */ false);
    try (DeflaterOutputStream defl = new DeflaterOutputStream(buffer, deflater)) {
      defl.write(plain);
    } catch (IOException e) {
      throw new IllegalStateException("compression.deflate: failed to write deflate stream", e);
    } finally {
      deflater.end();
    }
    return Base64.getEncoder().encodeToString(buffer.toByteArray());
  }

  private static String inflate(String input) {
    byte[] compressed = decodeBase64(input, "inflate");
    Inflater inflater = new Inflater(/* nowrap= */ false);
    try (InflaterInputStream infl = new InflaterInputStream(
            new ByteArrayInputStream(compressed), inflater)) {
      return new String(readAllBytes(infl), StandardCharsets.UTF_8);
    } catch (ZipException e) {
      throw new IllegalArgumentException("compression.inflate: invalid deflate payload", e);
    } catch (IOException e) {
      throw new IllegalArgumentException("compression.inflate: failed to read deflate stream", e);
    } finally {
      inflater.end();
    }
  }

  private static byte[] decodeBase64(String input, String mode) {
    try {
      return Base64.getDecoder().decode(input);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
              "compression." + mode + ": invalid base64 input", e);
    }
  }

  private static void validateLevel(int level) {
    if (level < -1 || level > 9) {
      throw new IllegalArgumentException(
              "compression.level must be between -1 and 9, was: " + level);
    }
  }

  private static byte[] readAllBytes(java.io.InputStream in) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] chunk = new byte[8192];
    int read;
    while ((read = in.read(chunk)) != -1) {
      out.write(chunk, 0, read);
    }
    return out.toByteArray();
  }
}
