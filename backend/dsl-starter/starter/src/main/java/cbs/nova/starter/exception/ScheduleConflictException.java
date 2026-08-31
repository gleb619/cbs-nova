package cbs.nova.starter.exception;

import lombok.Getter;

/**
 * Thrown when an attempt is made to create a Temporal schedule whose id already exists.
 */
@Getter
public class ScheduleConflictException extends RuntimeException {

  private final String scheduleId;

  public ScheduleConflictException(String scheduleId) {
    super("Schedule already exists: " + scheduleId);
    this.scheduleId = scheduleId;
  }
}
