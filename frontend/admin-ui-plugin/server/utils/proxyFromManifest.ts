import { getRouterParam, type H3Event, readBody } from 'h3'
import { type ProxyOptions, proxyToBackend } from './httpClient'

/**
 * T561 — a single OpenAPI-derived BFF proxy route.
 *
 * The canonical instances of this shape are GENERATED from docs/openapi.json
 * (`pnpm gen:bff-routes` → `server/api/v1/generated/`, mounted by module.ts).
 * `hasBody` / `explicitMethod` preserve the exact `proxyToBackend` call shape
 * the former hand-written routes used (asserted by server/api/v1 unit tests):
 * plain GETs pass no options object at all, bodyless POSTs pass only
 * `{ method }`, and body routes always include the `body` key.
 */
export interface BffRouteSpec {
  /** OpenAPI operationId — source of truth: docs/openapi.json. */
  operationId: string
  method: 'GET' | 'POST' | 'PUT' | 'DELETE'
  /** Backend (Spring Boot) path; may contain `{param}` placeholders. */
  backendPath: string
  /** Public BFF path this stub is mounted at; may contain `{param}` placeholders. */
  bffPath: string
  /** Path parameter names, in order of appearance in the paths. */
  params: string[]
  /** Forward the request body via readBody() (non-GET/DELETE body routes). */
  hasBody?: boolean
  /** Pass `{ method: 'GET' }` explicitly instead of omitting the options arg. */
  explicitMethod?: boolean
}

/**
 * Plain-proxy delegation for generated BFF routes. Interpolates the Nitro
 * router params into the backend path (verbatim, matching the legacy
 * hand-written `${param}` interpolation) and reuses `proxyToBackend` for
 * headers, auth refresh, error mapping and logging.
 */
export async function proxyFromManifest(event: H3Event, spec: BffRouteSpec): Promise<unknown> {
  let backendPath = spec.backendPath
  for (const param of spec.params) {
    const value = getRouterParam(event, param)
    backendPath = backendPath.replace(`{${param}}`, value ?? '')
  }

  if (spec.method === 'GET' && !spec.explicitMethod) {
    return proxyToBackend(event, backendPath)
  }

  const options: ProxyOptions = { method: spec.method }
  if (spec.hasBody) {
    options.body = await readBody(event)
  }
  return proxyToBackend(event, backendPath, options)
}
