package cbs.nova.starter.helper.model;

/**
 * Input for the built-in {@code httpAuth} helper.
 *
 * <p>
 * {@code mode} selects one of {@code "bearer"}, {@code "basic"}, {@code "apiKey"}, or
 * {@code "custom"} (case-insensitive). The remaining fields are interpreted based on the chosen
 * mode; see {@link cbs.nova.starter.helper.HttpAuthHelper} for details.
 */
public record HttpAuthIn(
        String mode, String token, String username, String password, String key, String header,
        String prefix, String value) {
}
