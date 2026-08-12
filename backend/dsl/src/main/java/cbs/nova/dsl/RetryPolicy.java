package cbs.nova.dsl;

import java.time.Duration;

//TODO: Move to package `backend/dsl/src/main/java/cbs/nova/dsl/model`
public record RetryPolicy(int maxAttempts, Duration initialInterval, double backoffCoefficient) {

}
