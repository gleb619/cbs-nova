package cbs.nova.starter.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cbs.nova.dsl.ExecutionMode;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Direct unit specs for {@link ManifestObjectGuardAdapter} (T738). The adapter bridges the
 * starter's {@link ManifestObjectGuard} to the platform {@link cbs.nova.dsl.security.ObjectGuard}
 * contract; these tests pin the translation (delegation, denial mapping, active flag).
 */
class ManifestObjectGuardAdapterTest {

  private static final String DEF = "OrderProcess";
  private static final String OBJECT_TYPE = "helper";
  private static final String OBJECT_NAME = "httpGet";
  private static final String CORRELATION_ID = "corr-xyz";
  private static final String PIECE_ID = "piece-1";
  private static final String REASON = "explicit deny piece matched";

  private ManifestObjectGuard delegate;
  private ManifestObjectGuardAdapter adapter;

  @BeforeEach
  void setUp() {
    delegate = mock(ManifestObjectGuard.class);
    adapter = new ManifestObjectGuardAdapter(delegate);
  }

  @Test
  void allowedInvocationMapsToEmptyOptional() {
    when(delegate.check(eq(ExecutionMode.PREVIEW), eq(DEF), eq(OBJECT_TYPE), eq(OBJECT_NAME),
            eq(CORRELATION_ID)))
            .thenReturn(Optional.empty());

    Optional<cbs.nova.dsl.security.ObjectGuard.Denial> denial = adapter.check(
            ExecutionMode.PREVIEW, DEF, OBJECT_TYPE, OBJECT_NAME, CORRELATION_ID);

    assertThat(denial).isEmpty();
  }

  @Test
  void denialIsTranslatedFieldByField() {
    when(delegate.check(eq(ExecutionMode.RUN), eq(DEF), eq(OBJECT_TYPE), eq(OBJECT_NAME),
            eq(CORRELATION_ID)))
            .thenReturn(Optional.of(new ManifestObjectGuard.Denial(DEF, PIECE_ID, OBJECT_TYPE,
                    OBJECT_NAME, REASON)));

    Optional<cbs.nova.dsl.security.ObjectGuard.Denial> denial = adapter.check(
            ExecutionMode.RUN, DEF, OBJECT_TYPE, OBJECT_NAME, CORRELATION_ID);

    assertThat(denial).isPresent();
    cbs.nova.dsl.security.ObjectGuard.Denial d = denial.get();
    assertThat(d.definitionName()).isEqualTo(DEF);
    assertThat(d.pieceId()).isEqualTo(PIECE_ID);
    assertThat(d.objectType()).isEqualTo(OBJECT_TYPE);
    assertThat(d.objectName()).isEqualTo(OBJECT_NAME);
    assertThat(d.reason()).isEqualTo(REASON);
  }

  @Test
  void denialWithNullPieceIdIsPreserved() {
    when(delegate.check(eq(ExecutionMode.PREVIEW), eq(DEF), eq(OBJECT_TYPE), eq(OBJECT_NAME),
            isNull()))
            .thenReturn(Optional.of(new ManifestObjectGuard.Denial(DEF, null, OBJECT_TYPE,
                    OBJECT_NAME, "no manifest piece covers this object")));

    Optional<cbs.nova.dsl.security.ObjectGuard.Denial> denial = adapter.check(
            ExecutionMode.PREVIEW, DEF, OBJECT_TYPE, OBJECT_NAME, null);

    assertThat(denial).isPresent();
    assertThat(denial.get().pieceId()).isNull();
  }

  @Test
  void denialWithNullDefinitionNameIsPreserved() {
    when(delegate.check(eq(ExecutionMode.PREVIEW), isNull(), eq(OBJECT_TYPE), eq(OBJECT_NAME),
            isNull()))
            .thenReturn(Optional.of(new ManifestObjectGuard.Denial(null, PIECE_ID, OBJECT_TYPE,
                    OBJECT_NAME, REASON)));

    Optional<cbs.nova.dsl.security.ObjectGuard.Denial> denial = adapter.check(
            ExecutionMode.PREVIEW, null, OBJECT_TYPE, OBJECT_NAME, null);

    assertThat(denial).isPresent();
    assertThat(denial.get().definitionName()).isNull();
  }

  @Test
  void activeDelegatesToUnderlyingGuard() {
    when(delegate.isActive()).thenReturn(true);
    assertThat(adapter.active()).isTrue();

    when(delegate.isActive()).thenReturn(false);
    assertThat(adapter.active()).isFalse();
  }

  @Test
  void allCheckArgsAreForwardedVerbatim() {
    when(delegate.check(eq(ExecutionMode.EXPLAIN), eq(DEF), eq(OBJECT_TYPE), eq(OBJECT_NAME),
            eq(CORRELATION_ID)))
            .thenReturn(Optional.empty());

    adapter.check(ExecutionMode.EXPLAIN, DEF, OBJECT_TYPE, OBJECT_NAME, CORRELATION_ID);

    verify(delegate).check(ExecutionMode.EXPLAIN, DEF, OBJECT_TYPE, OBJECT_NAME, CORRELATION_ID);
  }

  @Test
  void adaptedDenialRecordIsDistinctFromManifestRecord() {
    // The adapter must produce a platform Denial, not reuse the manifest one. Pin the type.
    when(delegate.check(eq(ExecutionMode.PREVIEW), eq(DEF), eq(OBJECT_TYPE), eq(OBJECT_NAME),
            eq(CORRELATION_ID)))
            .thenReturn(Optional.of(new ManifestObjectGuard.Denial(DEF, PIECE_ID, OBJECT_TYPE,
                    OBJECT_NAME, REASON)));

    Optional<cbs.nova.dsl.security.ObjectGuard.Denial> denial = adapter.check(
            ExecutionMode.PREVIEW, DEF, OBJECT_TYPE, OBJECT_NAME, CORRELATION_ID);

    assertThat(denial).isPresent();
    assertThat(denial.get())
            .isInstanceOf(cbs.nova.dsl.security.ObjectGuard.Denial.class)
            .isNotInstanceOf(ManifestObjectGuard.Denial.class);
  }
}
