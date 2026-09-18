package cbs.nova.starter.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Source of truth for {@code preCheck: feature-flag} evaluation in {@link PieceGuardFilter} (T549).
 *
 * <p>
 * The interface is deliberately minimal: a named flag is either enabled or not for the current
 * request. The request is passed so an implementation can vary flags per principal or tenant; the
 * shipped default ({@link PropertiesFeatureFlagSource}) ignores it and reads static configuration.
 *
 * <p>
 * A real feature-flag platform (remote config, percentage rollouts, …) can later implement this
 * interface as a bean — {@code PieceGuardFilterConfiguration} registers its beans with
 * {@code @ConditionalOnMissingBean}, so a custom {@code FeatureFlagSource} overrides the
 * properties-backed default without touching the guard.
 *
 * <p>
 * The plan file for T549 sketched the signature with
 * {@code org.springframework.web.servlet.function.ServerRequest}; the shipped seam uses
 * {@link HttpServletRequest} instead because the guard is a servlet filter and constructing a
 * {@code ServerRequest} per request would require message-converter plumbing for zero benefit.
 */
public interface FeatureFlagSource {

  /**
   * Whether the named flag is enabled for this request. Implementations must be side-effect free
   * and cheap — this runs on the hot request path.
   */
  boolean isEnabled(String flag, HttpServletRequest request);
}
