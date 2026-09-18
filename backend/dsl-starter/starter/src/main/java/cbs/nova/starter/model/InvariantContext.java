package cbs.nova.starter.model;

/**
 * Read-only snapshot of one successful execution, handed to {@code invariant-assert} post-check
 * hooks. The predicate set supported by {@code InvariantAssertHook} is deliberately tiny — named
 * conditions over the HTTP status — not an expression language (see {@code docs/vhs-manifest.md}).
 *
 * @param pieceId
 *          the manifest piece that executed
 * @param principalRole
 *          the caller's resolved {@code Role} name (T549 request attribute)
 * @param method
 *          the HTTP method of the executed route
 * @param path
 *          the request URI of the executed route
 * @param status
 *          the HTTP status the handler produced (always {@code < 400} — post-checks run on success
 *          only)
 */
public record InvariantContext(
        String pieceId,
        String principalRole,
        String method,
        String path,
        int status) {
}
