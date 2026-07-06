export function useDslApi() {
  async function getDefinitions() {
    return $fetch('/api/v1/dsl/definitions')
  }

  async function preview(name: string, body: unknown, metadata?: Record<string, unknown>) {
    return $fetch(`/api/v1/dsl/preview/${name}`, {
      method: 'POST',
      body: { body, metadata },
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

  return { getDefinitions, preview, run, explain, saveDraft, validateConstruct }
}
