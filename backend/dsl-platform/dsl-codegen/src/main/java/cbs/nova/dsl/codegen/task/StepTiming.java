package cbs.nova.dsl.codegen.task;

import java.time.Duration;

public record StepTiming(String phase, Duration duration) {
}
