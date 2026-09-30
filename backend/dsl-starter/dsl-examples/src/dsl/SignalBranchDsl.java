import cbs.nova.dslexamples.SignalBranchModels.*;
import java.util.Map;

/**
 * Branches on a Temporal signal. In run mode the signal arrives over the wire; in preview /
 * hierarchy / explain mode it is resolved from the seeded preview signal timeline
 * ({@code PreviewSignalBuffer}), so the same definition can be dry-run without a Temporal worker.
 */
List<DslObject> define() {
  return Dsl.process("SignalBranch")
      .input(SignalBranchIn.class)
      .output(SignalBranchOut.class)
      .signal("decision", Map.class)
      .execute(ctx -> {
        SignalBranchIn in = ctx.body();
        boolean seenBeforeAwait = ctx.signalReceived("decision");
        Object peek = ctx.getSignalPayload("decision", Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> decision = (Map<String, Object>) ctx.awaitSignal("decision", Map.class);
        boolean approved = Boolean.TRUE.equals(decision.get("approved"));
        String decisionText = approved ? "APPROVED" : "REJECTED";
        ctx.log("SignalBranch resolved " + decisionText + " for " + in.requestId());
        return Result.success(new SignalBranchOut(
                decisionText + ":" + in.amount(),
                in.requestId(),
                seenBeforeAwait && peek != null));
      })
      .buildList();
}