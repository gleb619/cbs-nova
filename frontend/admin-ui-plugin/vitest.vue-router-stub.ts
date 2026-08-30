/**
 * Test-only 'vue-router' shim, wired in via the `vue-router` alias in
 * vitest.config.ts.
 *
 * pnpm's strict node_modules layout does not hoist `vue-router` (a transitive
 * dep of nuxt) into a top-level node_modules, so a direct `import ... from
 * 'vue-router'` in a SUT fails under vitest even though it works at runtime
 * inside Nuxt. This shim exposes the two navigation-guard hooks the workbench
 * page actually uses. The real implementation depends on the active router
 * instance; for unit tests we capture the registered handler in module scope
 * so specs can invoke it directly without standing up a memory router.
 *
 * The capture array is reset in vitest.setup.ts' beforeEach so handlers from
 * previous specs do not leak.
 */

type RouteGuard = (
  to?: unknown,
  from?: unknown,
) => boolean | undefined | Promise<boolean | undefined>

const handlers: { leave?: RouteGuard; update?: RouteGuard } = {}

export function __resetRouterStub() {
  handlers.leave = undefined
  handlers.update = undefined
}

export function __getBeforeRouteLeaveGuard(): RouteGuard | undefined {
  return handlers.leave
}

export function onBeforeRouteLeave(guard: RouteGuard): void {
  handlers.leave = guard
}

export function onBeforeRouteUpdate(guard: RouteGuard): void {
  handlers.update = guard
}