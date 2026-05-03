package cbs.app.temporal.massop;

public record MassOpInput(
    String massOpCode, String performedBy, String dslVersion, String contextJson) {

}
