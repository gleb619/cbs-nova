package cbs.nova.starter.vhs.replay;

import cbs.nova.starter.vhs.TapeEvent;
import lombok.extern.slf4j.Slf4j;

/**
 * Safe-default {@link VhsCallDriver}: parses each recorded call, logs it, and returns a synthetic
 * success. Performs no network I/O and has no side effects.
 *
 * <p>
 * This is the default replay target ({@code cbs.vhs.replay.target=dry-run}). It exists so that tape
 * parsing, ordering, and timing can be exercised without touching any real environment.
 */
@Slf4j
public final class DryRunCallDriver implements VhsCallDriver {

  @Override
  public CallResult execute(TapeEvent callStartEvent) {
    TapeEvent.CallMetadata meta = callStartEvent.callMetadata();
    log.info(
            "[VHS dry-run] call={} type={} target={} operation={} input={}",
            meta != null ? meta.callId() : "?",
            meta != null ? meta.type() : "?",
            meta != null ? meta.target() : "?",
            meta != null ? meta.operation() : "?",
            callStartEvent.input());
    return CallResult.success(java.util.Map.of("dryRun", true));
  }
}
