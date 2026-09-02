package cbs.nova.starter.helper;

import static cbs.nova.starter.helper.HmacSha256Support.encodeRawBytes;
import static cbs.nova.starter.helper.HmacSha256Support.normalizeEncoding;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.Sha256In;
import cbs.nova.starter.helper.model.Sha256Out;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.jspecify.annotations.NonNull;

/**
 * Computes a plain SHA-256 content hash for a string.
 *
 * <p>
 * The helper accepts an {@code input} string and an optional {@code encoding}. Valid encodings
 * (case-insensitive) are {@code "hex"} (default), {@code "base64"}, and {@code "base64url"}. The
 * returned digest is computed over the UTF-8 bytes of the input.
 *
 * <p>
 * A null input is rejected with an {@link IllegalArgumentException}; an empty string is valid and
 * returns the hash of the empty byte sequence. A new {@link MessageDigest} instance is created for
 * each call because {@code MessageDigest} is not thread-safe.
 */
@Helper(name = "sha256")
public class Sha256Helper implements Executable<Sha256In, Sha256Out> {

  @Override
  public @NonNull Result<Sha256Out> execute(@NonNull Context<Sha256In> ctx) {
    try {
      Sha256In input = ctx.body();
      if (input.input() == null) {
        return Result.failure(new IllegalArgumentException("sha256.input is required"));
      }
      String encoding = normalizeEncoding(input.encoding());
      byte[] raw = MessageDigest.getInstance("SHA-256").digest(
              input.input().getBytes(StandardCharsets.UTF_8));
      String result = encodeRawBytes(raw, encoding);
      return Result.success(new Sha256Out(result));
    } catch (NoSuchAlgorithmException e) {
      return Result.failure(e);
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }
}
