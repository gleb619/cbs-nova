package cbs.nova.starter.model;

public record CompileDiagnostic(
        String file,
        Long line,
        Long column,
        String message,
        String severity) {

}
