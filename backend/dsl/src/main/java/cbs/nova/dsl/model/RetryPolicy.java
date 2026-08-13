package cbs.nova.dsl.model;

import java.time.Duration;

public record RetryPolicy(int maxAttempts, Duration initialInterval, double backoffCoefficient) {

}
