package cbs.nova.starter.security;

/**
 * Authorization roles for the DSL control-plane API (T408, phase 1).
 *
 * <p>
 * Roles are ordered from lowest to highest privilege. {@link #rank()} gives each role an integer
 * rank; a caller's role {@link #satisfies(Role)} the required role iff {@code caller.rank() >=
 * required.rank()}. Higher roles therefore always satisfy lower-role requirements.
 *
 * <p>
 * The five roles map to the documented tiers:
 * <ul>
 * <li>{@link #VIEWER} — read-only access to introspection, audit, diagnostics, schedules list,
 * drafts history, file reads, executions list.</li>
 * <li>{@link #RUNNER} — can run / preview / explain DSL processes and cancel in-flight
 * executions.</li>
 * <li>{@link #AUTHOR} — additionally writes drafts (save / publish / restore / delete), stages file
 * writes, flushes the file buffer, updates construct descriptions, imports definition bundles and
 * triggers a reload.</li>
 * <li>{@link #OPERATOR} — additionally manages Temporal schedules (create / delete) for DSL
 * definitions.</li>
 * <li>{@link #ADMIN} — bypasses all per-route role checks.</li>
 * </ul>
 *
 * <p>
 * Phase 1 maps API-key principals to {@link #ADMIN} (service-to-service trust). Phase 2 will
 * introduce a configurable demotion so an API key can be restricted to a lower tier.
 */
public enum Role {

  VIEWER(0), RUNNER(10), AUTHOR(20), OPERATOR(30), ADMIN(Integer.MAX_VALUE);

  private final int rank;

  Role(int rank) {
    this.rank = rank;
  }

  /**
   * Numeric rank used for hierarchy comparisons. Higher = more privileged.
   */
  public int rank() {
    return rank;
  }

  /**
   * Whether this role is at least as privileged as {@code required}. {@code ADMIN} satisfies every
   * required role including itself.
   */
  public boolean satisfies(Role required) {
    return required != null && this.rank >= required.rank();
  }
}
