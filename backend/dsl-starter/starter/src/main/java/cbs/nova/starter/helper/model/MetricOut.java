package cbs.nova.starter.helper.model;

/**
 * Output for the built-in {@code metric} helper.
 *
 * <p>
 * {@code emitted} is {@code true} when the meter was registered/updated against a Micrometer
 * {@code MeterRegistry} bean. When no registry is present in the Spring context (e.g. host apps
 * that don't pull in the actuator starter) the helper validates the input and returns
 * {@code emitted == false} as a no-op — validation still runs so a bad DSL fails fast.
 */
public record MetricOut(boolean emitted) {
}
