package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.ExecutionMode;
import cbs.nova.starter.config.properties.CbsDslManifestProperties;
import cbs.nova.starter.core.StarterConstants;
import cbs.nova.starter.model.ObjectAllow;
import cbs.nova.starter.model.ObjectDeny;
import cbs.nova.starter.model.Piece;
import cbs.nova.starter.model.Target;
import cbs.nova.starter.service.DslAuditService;
import cbs.nova.starter.service.DslCapabilityRegistry;
import cbs.nova.starter.service.PieceManifestService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

/**
 * Behaviour matrix for {@link ManifestObjectGuard} (T552): master feature flag, preview
 * deny-by-default, production permissive-with-audit vs strict, explicit deny pieces, and
 * correlation id / piece identity in audit + metrics.
 */
class ManifestObjectGuardTest {

  private static final String DEF = "OrderProcess";
  private static final String HELPER = "httpGet";
  private static final String CORRELATION_ID = "corr-123";

  private PieceManifestService manifestService;
  private DslAuditService auditService;
  private SimpleMeterRegistry meterRegistry;
  private ManifestObjectGuard guard;

  @BeforeEach
  void setUp() {
    manifestService = mock(PieceManifestService.class);
    auditService = mock(DslAuditService.class);
    meterRegistry = new SimpleMeterRegistry();
    guard = newGuard(true, CbsDslManifestProperties.ObjectMode.PERMISSIVE);
  }

  private ManifestObjectGuard newGuard(boolean enabled,
          CbsDslManifestProperties.ObjectMode mode) {
    CbsDslManifestProperties properties = new CbsDslManifestProperties(true,
            "classpath:piece-manifest.yaml", null, null, null,
            new CbsDslManifestProperties.ObjectEnforcement(enabled), mode);
    @SuppressWarnings("unchecked")
    ObjectProvider<DslAuditService> auditProvider = mock(ObjectProvider.class);
    when(auditProvider.getIfAvailable()).thenReturn(auditService);
    @SuppressWarnings("unchecked")
    ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterProvider = mock(
            ObjectProvider.class);
    when(meterProvider.getIfAvailable()).thenReturn(meterRegistry);
    @SuppressWarnings("unchecked")
    ObjectProvider<DslCapabilityRegistry> capabilityProvider = mock(ObjectProvider.class);
    when(capabilityProvider.getIfAvailable()).thenReturn(null);
    return new ManifestObjectGuard(properties, manifestService, auditProvider, meterProvider,
            capabilityProvider);
  }

  private static Piece allowPiece(Set<String> definitions, Set<String> helpers) {
    return new Piece("piece-allow", new Target.ObjectTarget("helper", HELPER),
            List.of(), List.of(), "deny",
            new ObjectAllow(definitions, helpers, Set.of()), null);
  }

  private static Piece denyPiece(Set<String> definitions) {
    return new Piece("piece-deny", new Target.ObjectTarget("helper", HELPER),
            List.of(), List.of(), "deny", null,
            new ObjectDeny(definitions, Set.of(), Set.of()));
  }

  // --- feature flag ------------------------------------------------------------

  @Test
  void enforcementDisabledAllowsEverythingWithoutAuditOrMetrics() {
    guard = newGuard(false, CbsDslManifestProperties.ObjectMode.STRICT);
    when(manifestService.findByObject("helper", HELPER)).thenReturn(List.of());

    Optional<ManifestObjectGuard.Denial> denial = guard.check(
            ExecutionMode.PREVIEW, DEF, "helper", HELPER, CORRELATION_ID);

    assertThat(denial).isEmpty();
    verify(auditService, never()).record(anyString(), anyString(), anyString(),
            any(), anyString(), any());
    assertThat(meterRegistry.find(StarterConstants.OBJECT_GUARD_DENIED_COUNTER).counters())
            .isEmpty();
  }

  // --- preview mode ------------------------------------------------------------

  @Test
  void previewDeniesUnlistedHelperByDefault() {
    when(manifestService.findByObject("helper", HELPER)).thenReturn(List.of());

    Optional<ManifestObjectGuard.Denial> denial = guard.check(
            ExecutionMode.PREVIEW, DEF, "helper", HELPER, CORRELATION_ID);

    assertThat(denial).isPresent();
    assertThat(denial.get().objectType()).isEqualTo("helper");
    assertThat(denial.get().objectName()).isEqualTo(HELPER);
    assertThat(denial.get().definitionName()).isEqualTo(DEF);
  }

  @Test
  void previewAllowsExplicitlyAllowlistedHelper() {
    when(manifestService.findByObject("helper", HELPER))
            .thenReturn(List.of(allowPiece(Set.of(DEF), Set.of())));

    Optional<ManifestObjectGuard.Denial> denial = guard.check(
            ExecutionMode.PREVIEW, DEF, "helper", HELPER, CORRELATION_ID);

    assertThat(denial).isEmpty();
    verify(auditService, never()).record(anyString(), anyString(), anyString(),
            any(), anyString(), any());
  }

  @Test
  void previewAllowsHelperListedInAllowHelpersScope() {
    when(manifestService.findByObject("helper", HELPER))
            .thenReturn(List.of(allowPiece(Set.of(), Set.of(HELPER))));

    Optional<ManifestObjectGuard.Denial> denial = guard.check(
            ExecutionMode.EXPLAIN, DEF, "helper", HELPER, CORRELATION_ID);

    assertThat(denial).isEmpty();
  }

  @Test
  void previewDeniesWhenObjectKnownToManifestButNotAllowlistedForDefinition() {
    when(manifestService.findByObject("helper", HELPER))
            .thenReturn(List.of(allowPiece(Set.of("OtherProcess"), Set.of())));

    Optional<ManifestObjectGuard.Denial> denial = guard.check(
            ExecutionMode.PREVIEW, DEF, "helper", HELPER, CORRELATION_ID);

    assertThat(denial).isPresent();
    assertThat(denial.get().reason()).contains("not allow-listed");
  }

  // --- production mode ---------------------------------------------------------

  @Test
  void productionPermissiveAllowsUnlistedHelperButWritesAuditRow() {
    when(manifestService.findByObject("helper", HELPER)).thenReturn(List.of());

    Optional<ManifestObjectGuard.Denial> denial = guard.check(
            ExecutionMode.RUN, DEF, "helper", HELPER, CORRELATION_ID);

    assertThat(denial).isEmpty();
    verify(auditService).record(anyString(),
            eq(StarterConstants.ACTION_OBJECT_GUARD_PERMISSIVE), eq("helper:" + HELPER),
            eq(CORRELATION_ID), eq(StarterConstants.OUTCOME_SUCCESS), any());
  }

  @Test
  void productionStrictDeniesUnlistedHelper() {
    guard = newGuard(true, CbsDslManifestProperties.ObjectMode.STRICT);
    when(manifestService.findByObject("helper", HELPER)).thenReturn(List.of());

    Optional<ManifestObjectGuard.Denial> denial = guard.check(
            ExecutionMode.RUN, DEF, "helper", HELPER, CORRELATION_ID);

    assertThat(denial).isPresent();
    assertThat(denial.get().reason()).contains("no manifest piece");
  }

  @Test
  void productionStrictStillAllowsAllowlistedHelper() {
    guard = newGuard(true, CbsDslManifestProperties.ObjectMode.STRICT);
    when(manifestService.findByObject("helper", HELPER))
            .thenReturn(List.of(allowPiece(Set.of(DEF), Set.of())));

    Optional<ManifestObjectGuard.Denial> denial = guard.check(
            ExecutionMode.RUN, DEF, "helper", HELPER, CORRELATION_ID);

    assertThat(denial).isEmpty();
  }

  // --- explicit deny pieces ----------------------------------------------------

  @Test
  void explicitDenyPieceDeniesEvenWhenProductionPermissive() {
    when(manifestService.findByObject("helper", HELPER))
            .thenReturn(List.of(denyPiece(Set.of(DEF))));

    Optional<ManifestObjectGuard.Denial> denial = guard.check(
            ExecutionMode.RUN, DEF, "helper", HELPER, CORRELATION_ID);

    assertThat(denial).isPresent();
    assertThat(denial.get().pieceId()).isEqualTo("piece-deny");
    assertThat(denial.get().reason()).contains("explicit deny");
  }

  @Test
  void explicitDenyPieceScopedToOtherDefinitionDoesNotBlockAllowPiece() {
    when(manifestService.findByObject("helper", HELPER))
            .thenReturn(List.of(denyPiece(Set.of("OtherProcess")),
                    allowPiece(Set.of(DEF), Set.of())));

    Optional<ManifestObjectGuard.Denial> denial = guard.check(
            ExecutionMode.RUN, DEF, "helper", HELPER, CORRELATION_ID);

    assertThat(denial).isEmpty();
  }

  // --- audit + metrics on denial -----------------------------------------------

  @Test
  void denialRecordsAuditWithPieceIdentityCorrelationIdAndIncrementsCounter() {
    when(manifestService.findByObject("helper", HELPER))
            .thenReturn(List.of(denyPiece(Set.of(DEF))));

    guard.check(ExecutionMode.PREVIEW, DEF, "helper", HELPER, CORRELATION_ID);

    ArgumentCaptor<Object> detailsCaptor = ArgumentCaptor.forClass(Object.class);
    verify(auditService).record(anyString(), eq(StarterConstants.ACTION_OBJECT_GUARD_DENY),
            eq("helper:" + HELPER), eq(CORRELATION_ID), eq(StarterConstants.OUTCOME_FAILURE),
            detailsCaptor.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> details = (Map<String, Object>) detailsCaptor.getValue();
    assertThat(details).containsEntry("objectType", "helper")
            .containsEntry("objectName", HELPER)
            .containsEntry("definitionName", DEF)
            .containsEntry("pieceId", "piece-deny");

    assertThat(meterRegistry.get(StarterConstants.OBJECT_GUARD_DENIED_COUNTER)
            .tag(StarterConstants.OBJECT_TYPE_TAG, "helper").counter().count()).isEqualTo(1.0);
  }

  @Test
  void denialWithoutAuditServiceStillIncrementsCounter() {
    @SuppressWarnings("unchecked")
    ObjectProvider<DslAuditService> auditProvider = mock(ObjectProvider.class);
    when(auditProvider.getIfAvailable()).thenReturn(null);
    @SuppressWarnings("unchecked")
    ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterProvider = mock(
            ObjectProvider.class);
    when(meterProvider.getIfAvailable()).thenReturn(meterRegistry);
    @SuppressWarnings("unchecked")
    ObjectProvider<DslCapabilityRegistry> capabilityProvider = mock(ObjectProvider.class);
    when(capabilityProvider.getIfAvailable()).thenReturn(null);
    CbsDslManifestProperties properties = new CbsDslManifestProperties(true,
            "classpath:piece-manifest.yaml", null, null, null,
            new CbsDslManifestProperties.ObjectEnforcement(true),
            CbsDslManifestProperties.ObjectMode.PERMISSIVE);
    ManifestObjectGuard strictGuard = new ManifestObjectGuard(properties, manifestService,
            auditProvider, meterProvider, capabilityProvider);
    when(manifestService.findByObject("helper", HELPER)).thenReturn(List.of());

    Optional<ManifestObjectGuard.Denial> denial = strictGuard.check(
            ExecutionMode.PREVIEW, DEF, "helper", HELPER, CORRELATION_ID);

    assertThat(denial).isPresent();
    assertThat(meterRegistry.get(StarterConstants.OBJECT_GUARD_DENIED_COUNTER).counter().count())
            .isEqualTo(1.0);
  }

  @Test
  void checkToleratesCapabilityRegistryFailure() {
    @SuppressWarnings("unchecked")
    ObjectProvider<DslCapabilityRegistry> capabilityProvider = mock(ObjectProvider.class);
    DslCapabilityRegistry failing = mock(DslCapabilityRegistry.class);
    when(failing.forDefinition(anyString()))
            .thenThrow(new RuntimeException("boom"));
    when(capabilityProvider.getIfAvailable()).thenReturn(failing);
    @SuppressWarnings("unchecked")
    ObjectProvider<DslAuditService> auditProvider = mock(ObjectProvider.class);
    when(auditProvider.getIfAvailable()).thenReturn(auditService);
    @SuppressWarnings("unchecked")
    ObjectProvider<io.micrometer.core.instrument.MeterRegistry> meterProvider = mock(
            ObjectProvider.class);
    when(meterProvider.getIfAvailable()).thenReturn(meterRegistry);
    CbsDslManifestProperties properties = new CbsDslManifestProperties(true,
            "classpath:piece-manifest.yaml", null, null, null,
            new CbsDslManifestProperties.ObjectEnforcement(true),
            CbsDslManifestProperties.ObjectMode.PERMISSIVE);
    ManifestObjectGuard resilientGuard = new ManifestObjectGuard(properties, manifestService,
            auditProvider, meterProvider, capabilityProvider);
    when(manifestService.findByObject("helper", HELPER))
            .thenReturn(List.of(allowPiece(Set.of(DEF), Set.of())));

    Optional<ManifestObjectGuard.Denial> denial = resilientGuard.check(
            ExecutionMode.PREVIEW, DEF, "helper", HELPER, CORRELATION_ID);

    assertThat(denial).isEmpty();
  }

  @Test
  void isActiveReflectsFeatureFlag() {
    assertThat(guard.isActive()).isTrue();
    assertThat(newGuard(false, CbsDslManifestProperties.ObjectMode.PERMISSIVE).isActive())
            .isFalse();
  }
}
