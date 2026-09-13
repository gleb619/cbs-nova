import cbs.nova.dslexamples.v1.JwtHttpCallModels.*;
import cbs.nova.starter.helper.model.HttpAuthIn;
import cbs.nova.starter.helper.model.HttpAuthOut;
import cbs.nova.starter.helper.model.HttpCallIn;
import cbs.nova.starter.helper.model.HttpCallOut;
import cbs.nova.starter.helper.model.JwtIn;
import cbs.nova.starter.helper.model.JwtOut;
import java.util.Map;

List<DslObject> define() {
  return Dsl.process("JwtHttpCall")
      .input(AuthApiIn.class)
      .output(AuthApiOut.class)
      .execute(ctx -> {
        AuthApiIn in = ctx.body();

        var verified = ctx.runHelper("jwt",
            new JwtIn("verify", in.token(), in.secret(), "HS256", null, null, null));
        if (!verified.isSuccess()) {
          return Result.failure(verified.cause());
        }
        JwtOut jwtOut = verified.as(JwtOut.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) jwtOut.result();
        @SuppressWarnings("unchecked")
        Map<String, Object> claims = (Map<String, Object>) payload.get("payload");
        String subject = claims != null ? String.valueOf(claims.get("sub")) : null;

        var auth = ctx.runHelper("httpAuth",
            new HttpAuthIn("bearer", in.token(), null, null, null, null, null, null));
        if (!auth.isSuccess()) {
          return Result.failure(auth.cause());
        }
        HttpAuthOut authOut = auth.as(HttpAuthOut.class);

        String method = in.method() != null && !in.method().isBlank()
            ? in.method().toUpperCase()
            : "GET";
        var call = ctx.runHelper("httpCall",
            new HttpCallIn(in.url(), method, authOut.headers(), in.body(), null, null, null));
        if (!call.isSuccess()) {
          return Result.failure(call.cause());
        }
        HttpCallOut response = call.as(HttpCallOut.class);

        return Result.success(new AuthApiOut(
            response.status(), response.bodyOrEmpty(), subject, true));
      })
      .buildList();
}
