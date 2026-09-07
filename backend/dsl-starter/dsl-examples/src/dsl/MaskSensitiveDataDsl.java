import cbs.nova.dslexamples.MaskSensitiveDataModels.*;
import cbs.nova.starter.helper.model.FormatMessageIn;
import cbs.nova.starter.helper.model.FormatMessageOut;
import cbs.nova.starter.helper.model.MaskIn;
import cbs.nova.starter.helper.model.MaskOut;

List<DslObject> define() {
  return Dsl.process("MaskSensitiveData")
      .input(MaskingIn.class)
      .output(MaskingOut.class)
      .execute(ctx -> {
        MaskingIn in = ctx.body();

        // Safe default: keep last 4 of a long value, fixed width for short ones.
        var maskedCard = ctx.runHelper("mask",
            new MaskIn(in.cardNumber(), null, null, null, null, null));
        if (!maskedCard.isSuccess()) {
          return Result.failure(maskedCard.cause());
        }
        String safeCard = maskedCard.as(MaskOut.class).result();

        var rendered = ctx.runHelper("formatMessage",
            new FormatMessageIn(in.notificationTemplate(), Map.of("card", safeCard)));
        if (!rendered.isSuccess()) {
          return Result.failure(rendered.cause());
        }
        FormatMessageOut message = rendered.as(FormatMessageOut.class);

        // Redact an audit-detail field before it lands in details_json:
        // show the first/last 2 code points, mask the middle with '#'.
        var redacted = ctx.runHelper("mask",
            new MaskIn(in.payerReference(), "edges", 2, 2, "#", null));
        if (!redacted.isSuccess()) {
          return Result.failure(redacted.cause());
        }

        Map<String, Object> auditDetails = new LinkedHashMap<>();
        auditDetails.put("payerReference", redacted.as(MaskOut.class).result());
        auditDetails.put("amount", in.amount());

        return Result.success(new MaskingOut(safeCard, message.result(), auditDetails));
      })
      .buildList();
}
