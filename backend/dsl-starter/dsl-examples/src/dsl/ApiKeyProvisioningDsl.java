import cbs.nova.dslexamples.ApiKeyProvisioningModels.*;
import cbs.nova.starter.helper.model.SecretIn;
import cbs.nova.starter.helper.model.SecretOut;


List<DslObject> define() {
  return Dsl.process("ApiKeyProvisioning")
      .input(ApiKeyProvisioningIn.class)
      .output(ApiKeyProvisioningOut.class)
      .description("Provisions a cryptographic API key and idempotency key using the `secret` helper. The `secret` helper is backed by SecureRandom and intended for security-sensitive values (API keys, tokens, signing secrets, idempotency keys). This is the security-sensitive counterpart to the non-cryptographic `random` helper demonstrated in the SampleDataGeneration example.")
      .execute(ctx -> {
        ApiKeyProvisioningIn in = ctx.body();

        // token mode — 32-char URL-safe API key (192 bits entropy, per helper javadoc).
        var apiKeyVar = ctx.runHelper("secret",
            new SecretIn("token", 32, null));
        if (!apiKeyVar.isSuccess()) {
          return Result.failure(apiKeyVar.cause());
        }
        String apiKey = apiKeyVar.as(SecretOut.class).result();

        // bytes mode — 16-byte idempotency key, hex-encoded (128 bits).
        var idempotencyVar = ctx.runHelper("secret",
            new SecretIn("bytes", 16, "hex"));
        if (!idempotencyVar.isSuccess()) {
          return Result.failure(idempotencyVar.cause());
        }
        String idempotencyKey = idempotencyVar.as(SecretOut.class).result();

        return Result.success(new ApiKeyProvisioningOut(
            in.purpose(), apiKey, idempotencyKey));
      })
      .buildList();
}
