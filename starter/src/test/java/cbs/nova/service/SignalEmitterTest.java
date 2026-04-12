package cbs.nova.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SignalEmitterTest {

  @Mock
  private MassOpSignalPort signalPort;

  @InjectMocks
  private SignalEmitter signalEmitter;

  @Test
  @DisplayName("shouldEmitLockSignalToWorkflow")
  void shouldEmitLockSignalToWorkflow() {
    signalEmitter.emit("massop-DAILY_INTEREST-abc123", "LOCK");

    verify(signalPort).signal("massop-DAILY_INTEREST-abc123", "LOCK");
  }

  @Test
  @DisplayName("shouldEmitUnlockSignalToWorkflow")
  void shouldEmitUnlockSignalToWorkflow() {
    signalEmitter.emit("massop-DAILY_INTEREST-abc123", "UNLOCK");

    verify(signalPort).signal("massop-DAILY_INTEREST-abc123", "UNLOCK");
  }
}
