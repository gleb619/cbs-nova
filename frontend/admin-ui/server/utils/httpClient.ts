import type { H3Event } from 'h3'

export async function proxyToBackend<T>(
  _event: H3Event,
  path: string,
  options: { method?: string; body?: unknown } = {},
): Promise<T> {
  const { baseUrl, apiKey } = useBackendConfig()
  const url = `${baseUrl.replace(/\/$/, '')}${path}`
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  }
  if (apiKey) headers['X-Api-Key'] = apiKey

  try {
    return (await $fetch<T>(url, {
      method: options.method ?? 'GET',
      headers,
      body: options.body,
    })) as T
  } catch (err: unknown) {
    const fetchError = err as {
      response?: { status?: number }
      data?: { message?: string; code?: string; details?: unknown }
    }
    const status = fetchError.response?.status ?? 500
    const data = fetchError.data ?? {
      message: 'Backend error',
      code: 'BACKEND_ERROR',
      details: null,
    }
    throw createError({
      statusCode: status,
      statusMessage: data.message ?? 'Backend error',
      data: { message: data.message, code: data.code, details: data.details },
    })
  }
}
