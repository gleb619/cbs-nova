export function useDslApi() {
  async function getDefinitions() {
    return $fetch('/api/v1/dsl/definitions')
  }

  async function preview(
    name: string,
    body: unknown,
    metadata?: Record<string, unknown>,
    mocks?: Record<string, Record<string, unknown>>,
  ) {
    const hasMocks = !!mocks && Object.keys(mocks).length > 0
    return $fetch(`/api/v1/dsl/preview/${name}`, {
      method: 'POST',
      body: hasMocks ? { body, metadata, mocks } : { body, metadata },
    })
  }

  async function run(name: string, body: unknown, metadata?: Record<string, unknown>) {
    return $fetch(`/api/v1/dsl/run/${name}`, {
      method: 'POST',
      body: { body, metadata },
    })
  }

  async function explain(name: string, body: unknown, metadata?: Record<string, unknown>) {
    return $fetch(`/api/v1/dsl/explain/${name}`, {
      method: 'POST',
      body: { body, metadata },
    })
  }

  async function saveDraft(name: string, payload: unknown) {
    // stub — endpoint TBD
    return Promise.resolve({ success: true, name, payload })
  }

  async function validateConstruct(name: string) {
    // stub — calls preview to validate
    return preview(name, {})
  }

  async function reload() {
    return $fetch('/api/v1/dsl/reload', { method: 'POST' })
  }

  return { getDefinitions, preview, run, explain, saveDraft, validateConstruct, reload }
}
