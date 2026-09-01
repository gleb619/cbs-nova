package cbs.nova.starter.helper;

import static cbs.nova.starter.helper.HmacSha256Support.encodeRawBytes;
import static cbs.nova.starter.helper.HmacSha256Support.normalizeEncoding;
import static cbs.nova.starter.helper.HmacSha256Support.signToRawBytes;

import cbs.nova.dsl.Context;
import cbs.nova.dsl.Executable;
import cbs.nova.dsl.Result;
import cbs.nova.dsl.annotation.Helper;
import cbs.nova.starter.helper.model.HmacSha256SignIn;
import cbs.nova.starter.helper.model.HmacSha256SignOut;
import java.security.GeneralSecurityException;
import org.jspecify.annotations.NonNull;

/**
 * Signs a message using HMAC-SHA256.
 *
 * <p>
 * The helper accepts a {@code message}, a {@code secret}, and an optional {@code encoding}. Valid
 * encodings (case-insensitive) are {@code "hex"} (default), {@code "base64"}, and
 * {@code "base64url"}. The returned signature is encoded with the requested format.
 */
@Helper(name = "hmacSha256Sign")
public class HmacSha256SignHelper implements Executable<HmacSha256SignIn, HmacSha256SignOut> {

  @Override
  public @NonNull Result<HmacSha256SignOut> execute(@NonNull Context<HmacSha256SignIn> ctx) {
    try {
      HmacSha256SignIn input = ctx.body();
      if (input.message() == null) {
        return Result.failure(new IllegalArgumentException("hmacSha256Sign.message is required"));
      }
      if (input.secret() == null || input.secret().isEmpty()) {
        return Result
                .failure(new IllegalArgumentException("hmacSha256Sign.secret must not be empty"));
      }
      String encoding = normalizeEncoding(input.encoding());
      byte[] raw = signToRawBytes(input.message(), input.secret());
      String signature = encodeRawBytes(raw, encoding);
      return Result.success(new HmacSha256SignOut(signature, encoding));
    } catch (GeneralSecurityException e) {
      return Result.failure(e);
    } catch (RuntimeException e) {
      return Result.failure(e);
    }
  }
}
