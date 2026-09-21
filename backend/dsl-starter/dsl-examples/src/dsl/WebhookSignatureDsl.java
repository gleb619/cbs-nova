import cbs.nova.dslexamples.WebhookSignatureModels.*;
import cbs.nova.starter.helper.model.HmacSha256SignIn;
import cbs.nova.starter.helper.model.HmacSha256SignOut;
import cbs.nova.starter.helper.model.HmacSha256VerifyIn;
import cbs.nova.starter.helper.model.HmacSha256VerifyOut;


List<DslObject> define() {
  return Dsl.process("WebhookSignature")
      .input(WebhookSignatureIn.class)
      .output(WebhookSignatureOut.class)
      .description("Demonstrates an outbound/inbound HMAC-SHA256 webhook signature handshake: signs an outbound payload with a shared secret to build the X-Signature header value, then verifies three scenarios — same payload with the same secret (accept), tampered payload with the original signature (reject), and the original payload with the wrong secret (reject). The accepted scenario proves end-to-end sign+verify round-trip; the two rejected scenarios pin the constant-time comparison behaviour for the two common webhook-tampering failure modes.")
      .execute(ctx -> {
        WebhookSignatureIn in = ctx.body();

        // Outbound: sign the payload with the shared secret.
        var signedVar = ctx.runHelper("hmacSha256Sign",
            new HmacSha256SignIn(in.payload(), in.secret(), in.encoding()));
        if (!signedVar.isSuccess()) {
          return Result.failure(signedVar.cause());
        }
        HmacSha256SignOut signed = signedVar.as(HmacSha256SignOut.class);
        String outboundSignature = signed.signature();
        String outboundHeader = "sha256=" + outboundSignature;

        // Inbound (a): same payload + same secret — accept.
        var okVar = ctx.runHelper("hmacSha256Verify",
            new HmacSha256VerifyIn(in.payload(), in.secret(), outboundSignature, in.encoding()));
        if (!okVar.isSuccess()) {
          return Result.failure(okVar.cause());
        }
        boolean validSignedPayload = okVar.as(HmacSha256VerifyOut.class).valid();

        // Inbound (b): tampered payload + original signature — reject.
        var tamperVar = ctx.runHelper("hmacSha256Verify",
            new HmacSha256VerifyIn(in.tamperedPayload(), in.secret(), outboundSignature, in.encoding()));
        if (!tamperVar.isSuccess()) {
          return Result.failure(tamperVar.cause());
        }
        boolean validTamperedPayload = tamperVar.as(HmacSha256VerifyOut.class).valid();

        // Inbound (c): original payload + wrong secret — reject.
        var wrongVar = ctx.runHelper("hmacSha256Verify",
            new HmacSha256VerifyIn(in.payload(), in.wrongSecret(), outboundSignature, in.encoding()));
        if (!wrongVar.isSuccess()) {
          return Result.failure(wrongVar.cause());
        }
        boolean validWrongSecret = wrongVar.as(HmacSha256VerifyOut.class).valid();

        return Result.success(new WebhookSignatureOut(
            outboundSignature,
            outboundHeader,
            validSignedPayload,
            validTamperedPayload,
            validWrongSecret,
            signed.encoding()));
      })
      .buildList();
}
