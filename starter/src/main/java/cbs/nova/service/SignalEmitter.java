package cbs.nova.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class SignalEmitter {

  private final MassOpSignalPort signalPort;

  /**
   * Sends a signal to a running MassOpWorkflow.
   *
   * @param temporalWorkflowId the Temporal workflow ID
   * @param signalType the signal type: "LOCK" or "UNLOCK"
   */
  public void emit(String temporalWorkflowId, String signalType) {
    log.debug("Emitting signal {} to workflow {}", signalType, temporalWorkflowId);
    signalPort.signal(temporalWorkflowId, signalType);
  }
}
