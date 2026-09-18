package cbs.nova.starter.service.check;

import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.PostCheck;
import cbs.nova.starter.service.DslAuditService;

import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;

/**
 * {@code postCheck: audit-write} — appends a SUCCESS {@code dsl_audit} row with the check's
 * {@code action} code. Reuses {@link DslAuditService} verbatim (action/outcome columns, fail-safe
 * warn+swallow); a no-op when no {@link DslAuditService} bean exists.
 */
public class AuditWriteHook implements PostCheckHook {

  private final @Nullable ObjectProvider<DslAuditService> auditServiceProvider;

  public AuditWriteHook(@Nullable ObjectProvider<DslAuditService> auditServiceProvider) {
    this.auditServiceProvider = auditServiceProvider;
  }

  @Override
  public String type() {
    return "audit-write";
  }

  @Override
  public void run(PostCheck check, Invocation invocation) {
    if (!(check instanceof PostCheck.AuditWriteCheck auditWrite)) {
      throw new IllegalArgumentException("audit-write hook given a " + check.type() + " check");
    }
    DslAuditService auditService = auditServiceProvider == null
            ? null
            : auditServiceProvider.getIfAvailable();
    if (auditService == null) {
      return;
    }
    String actor = invocation.actor() != null ? invocation.actor() : DslAuditService.currentActor();
    auditService.record(actor, auditWrite.action(), invocation.piece().id(),
            invocation.correlationId(), StarterConstants.OUTCOME_SUCCESS,
            Map.of("hook", "audit-write", "route",
                    invocation.context().method() + " " + invocation.context().path()));
  }
}
