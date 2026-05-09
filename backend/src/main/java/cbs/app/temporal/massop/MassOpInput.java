package cbs.app.temporal.massop;

// TODO: remove
@Deprecated(forRemoval = true)
public record MassOpInput(
    String massOpCode, String performedBy, String dslVersion, String contextJson) {}
