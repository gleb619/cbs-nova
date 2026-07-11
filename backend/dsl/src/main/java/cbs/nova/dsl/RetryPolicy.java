package cbs.nova.dsl;

import java.time.Duration;

public record RetryPolicy(int maxAttempts, Duration initialInterval, double backoffCoefficient) {

}
