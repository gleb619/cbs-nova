package cbs.nova.starter.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Thrown when an attempt is made to create a Temporal schedule whose id already exists.
 */
@Getter
@RequiredArgsConstructor
public class ScheduleConflictException extends RuntimeException {

  private final String scheduleId;

  @Override
  public String getMessage() {
    return "Schedule already exists: " + scheduleId;
  }
}
