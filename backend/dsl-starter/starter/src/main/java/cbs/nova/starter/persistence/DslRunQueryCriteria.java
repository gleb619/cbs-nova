package cbs.nova.starter.persistence;

import com.github.squigglesql.squigglesql.FunctionCall;
import com.github.squigglesql.squigglesql.Matchable;
import com.github.squigglesql.squigglesql.Selectable;
import com.github.squigglesql.squigglesql.TableReference;
import com.github.squigglesql.squigglesql.criteria.Criteria;
import com.github.squigglesql.squigglesql.literal.Literal;
import java.util.List;

final class DslRunQueryCriteria {

  private DslRunQueryCriteria() {
  }

  static Criteria matchesProcessName(DslRunTableColumns t, TableReference r, String processName) {
    return Criteria.equal(r.get(t.processName()), Literal.of(processName));
  }

  static Criteria matchesStatusIgnoreCase(DslRunTableColumns t, TableReference r, String status) {
    return Criteria.equal(lower(r.get(t.status())), lower(Literal.of(status)));
  }

  static Criteria matchesModeIgnoreCase(DslRunTableColumns t, TableReference r, String mode) {
    FunctionCall nullIfBlank = new FunctionCall("NULLIF", r.get(t.executionMode()), Literal.of(""));
    FunctionCall coalesced = new FunctionCall("COALESCE", nullIfBlank, Literal.of("RUN"));
    return Criteria.equal(lower(coalesced), lower(Literal.of(mode)));
  }

  static Criteria matchesCorrelationId(
          DslRunTableColumns t, TableReference r, String correlationId) {
    return Criteria.equal(r.get(t.correlationId()), Literal.of(correlationId));
  }

  static List<Selectable> fullSelection(DslRunTableColumns t, TableReference r) {
    return List.of(
            r.get(t.id()),
            r.get(t.runId()),
            r.get(t.processName()),
            r.get(t.status()),
            r.get(t.inputJson()),
            r.get(t.outputJson()),
            r.get(t.errorMessage()),
            r.get(t.contextJson()),
            r.get(t.startedAt()),
            r.get(t.finishedAt()),
            r.get(t.executionMode()),
            r.get(t.triggeredBy()),
            r.get(t.correlationId()));
  }

  static FunctionCall minuteBucket(DslRunTableColumns t, TableReference r) {
    return new FunctionCall("date_trunc", Literal.of("minute"), r.get(t.startedAt()));
  }

  static FunctionCall lower(Matchable argument) {
    return new FunctionCall("LOWER", argument);
  }
}
