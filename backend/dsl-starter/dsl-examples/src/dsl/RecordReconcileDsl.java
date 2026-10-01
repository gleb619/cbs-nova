import cbs.nova.dslexamples.RecordReconcileModels.*;
import cbs.nova.starter.helper.model.JsonDiffIn;
import cbs.nova.starter.helper.model.JsonDiffOut;
import cbs.nova.starter.helper.model.JsonDiffOut.Change;
import cbs.nova.starter.helper.model.SimilarityIn;
import cbs.nova.starter.helper.model.SimilarityOut;
import java.util.List;
import java.util.Map;

List<DslObject> define() {
  return Dsl.process("RecordReconcile")
      .input(RecordReconcileIn.class)
      .output(RecordReconcileOut.class)
      .description("Deduplicates a candidate record against an existing list via the similarity"
          + " helper (jaroWinkler / ratio / levenshtein modes), and diffs the matched candidate's"
          + " JSON payload against the new candidate JSON via the jsonDiff helper to surface"
          + " field-level changes. Mirrors a customer-record reconciliation flow.")
      .execute(ctx -> {
        RecordReconcileIn in = ctx.body();

        // Resolve a stable name field per record. A null candidateField falls back to "name",
        // matching the helper docs convention used by recordOps / listOps.
        String nameField = in.candidateField() == null ? "name" : in.candidateField();
        // Default mode = "jaroWinkler" — well suited to person-name matching.
        String mode = in.mode() == null ? "jaroWinkler" : in.mode();

        // Dedupe pass: walk every candidate, score against candidateName, keep the best.
        int bestIndex = -1;
        String bestName = "";
        double bestScore = 0.0;
        for (int i = 0; i < in.candidates().size(); i++) {
          Map<String, Object> entry = in.candidates().get(i);
          Object raw = entry == null ? null : entry.get(nameField);
          String other = raw == null ? "" : String.valueOf(raw);
          var scored = ctx.runHelper("similarity",
              new SimilarityIn(mode, in.candidateName(), other, false));
          if (!scored.isSuccess()) {
            return Result.failure(scored.cause());
          }
          double score = scored.as(SimilarityOut.class).score();
          if (score > bestScore) {
            bestScore = score;
            bestIndex = i;
            bestName = other;
          }
        }
        boolean matched = bestIndex >= 0 && bestScore >= in.matchThreshold();

        // Reconcile pass: serialize the matched candidate as JSON, diff against the
        // candidate JSON. When no candidate clears the threshold we still call jsonDiff
        // against an empty object so the output shape is uniform — `identical` will be
        // false and `changeCount` records the full payload as added entries.
        Map<String, Object> matchedRecord = matched
            ? in.candidates().get(bestIndex)
            : Map.of();
        String matchedJson = serializeAsJson(matchedRecord == null ? Map.of() : matchedRecord);

        var diff = ctx.runHelper("jsonDiff",
            new JsonDiffIn(matchedJson, in.candidateJson()));
        if (!diff.isSuccess()) {
          return Result.failure(diff.cause());
        }
        JsonDiffOut diffOut = diff.as(JsonDiffOut.class);

        List<Change> changes = diffOut.changes();
        String firstPath = changes.isEmpty() ? null : changes.get(0).path();
        String firstOp = changes.isEmpty() ? null : changes.get(0).op();

        return Result.success(new RecordReconcileOut(
            mode,
            in.candidateName(),
            matched ? bestName : "",
            bestScore,
            in.matchThreshold(),
            matched,
            matched ? bestIndex : -1,
            diffOut.identical(),
            changes.size(),
            firstPath,
            firstOp));
      })
      .buildList();
}

// Serialize a record map as a compact JSON object. The DSL examples module intentionally
// has no Jackson dependency, so we hand-roll a tiny encoder for the limited shape the
// jsonDiff helper accepts (flat string-keyed maps with String / Number / Boolean / null
// values, in iteration order). Nested maps/lists render as empty objects so callers
// feeding nested payloads reach the jsonDiff helper unchanged.
String serializeAsJson(Map<String, Object> record) {
  if (record == null || record.isEmpty()) {
    return "{}";
  }
  StringBuilder sb = new StringBuilder("{");
  boolean first = true;
  for (Map.Entry<String, Object> entry : record.entrySet()) {
    if (!first) {
      sb.append(',');
    }
    first = false;
    sb.append('"').append(jsonEscape(entry.getKey())).append('"').append(':');
    Object value = entry.getValue();
    if (value == null) {
      sb.append("null");
    } else if (value instanceof Number || value instanceof Boolean) {
      sb.append(value.toString());
    } else {
      sb.append('"').append(jsonEscape(value.toString())).append('"');
    }
  }
  return sb.append('}').toString();
}

String jsonEscape(String raw) {
  StringBuilder out = new StringBuilder();
  for (int i = 0; i < raw.length(); i++) {
    char c = raw.charAt(i);
    switch (c) {
      case '"' -> out.append("\\\"");
      case '\\' -> out.append("\\\\");
      case '\n' -> out.append("\\n");
      case '\r' -> out.append("\\r");
      case '\t' -> out.append("\\t");
      default -> out.append(c);
    }
  }
  return out.toString();
}