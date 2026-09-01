package cbs.nova.starter.helper;

import static cbs.nova.starter.helper.HmacSha256Support.constantTimeEquals;
import static cbs.nova.starter.helper.HmacSha256Support.decodeSignature;
import static cbs.nova.starter.helper.HmacSha256Support.encodeRawBytes;
import static cbs.nova.starter.helper.HmacSha256Support.normalizeEncoding;
import static cbs.nova.starter.helper.HmacSha256Support.signToRawBytes;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.HmacSha256VerifyIn;
import cbs.nova.starter.helper.model.HmacSha256VerifyOut;
import java.security.GeneralSecurityException;
import org.jspecify.annotations.NonNull;

/**
 * Verifies a message signature using HMAC-SHA256.
 *
 * <p>
 * The helper accepts a {@code message}, a {@code secret}, a {@code signature}, and an optional
 * {@code encoding}. Valid encodings (case-insensitive) are {@code "hex"} (default),
 * {@code "base64"}, and {@code "base64url"}. The expected signature is recomputed and compared to
 * the provided signature using a constant-time byte comparison. A malformed provided signature does
 * not raise an error; it simply yields {@code valid=false}.
 */
@Helper(name = "hmacSha256Verify")
public class HmacSha256VerifyHelper implements Executable<HmacSha256VerifyIn, HmacSha256VerifyOut> {

  @Override
  public @NonNull Result<HmacSha256VerifyOut> execute(@NonNull Context<HmacSha256VerifyIn> ctx) {
    try {
      HmacSha256VerifyIn input = ctx.body();
      if (input.message() == null) {
        return Result.failure(new IllegalArgumentException("hmacSha256Verify.message is required"));
      }
      if (input.secret() == null || input.secret().isEmpty()) {
        return Result.failure(
                new IllegalArgumentException("hmacSha256Verify.secret must not be empty"));
      }
      if (input.signature() == null || input.signature().isBlank()) {
        return Result
                .failure(new IllegalArgumentException("hmacSha256Verify.signature is required"));
      }
      String encoding = normalizeEncoding(input.encoding());
      byte[] expected;
      try {
        expected = signToRawBytes(input.message(), input.secret());
      } catch (GeneralSecurityException e) {
        return Result.failure(e);
      }
      byte[] provided;
      try {
        provided = decodeSignature(input.signature(), encoding);
      } catch (RuntimeException e) {
        return Result.success(new HmacSha256VerifyOut(false));
      }
      boolean valid = constantTimeEquals(provided, expected);
      return Result.success(new HmacSha256VerifyOut(valid));
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }
}
