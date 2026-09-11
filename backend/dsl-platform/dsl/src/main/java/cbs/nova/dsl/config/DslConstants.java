package cbs.nova.dsl.config;

import java.time.Duration;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DslConstants {

  public static final String DEFAULT_TASK_QUEUE = "NONE";

  public static final String DEFAULT_VERSION = "v1";

  public static final Duration DEFAULT_START_TO_CLOSE_TIMEOUT = Duration.ZERO;

  public static final Duration DEFAULT_HEARTBEAT_TIMEOUT = Duration.ZERO;

}
