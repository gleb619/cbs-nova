import type { RunnerMode, RunnerOutput, RunnerStatus } from '../types/runner'
import { useDslApi } from './useDslApi'

const selectedDefinition = ref<string | null>(null)
const mode = ref<RunnerMode>('preview')
const status = ref<RunnerStatus>('idle')
const formData = ref<Record<string, unknown>>({})
const output = ref<RunnerOutput | null>(null)
const showConfirmModal = ref(false)

export function useRunner() {
  function selectDefinition(name: string | null) {
    selectedDefinition.value = name
    resetOutput()
  }

  function setMode(next: RunnerMode) {
    mode.value = next
  }

  function resetOutput() {
    output.value = null
    status.value = 'idle'
  }

  async function submit() {
    const name = selectedDefinition.value
    if (!name) {
      status.value = 'failed'
      output.value = { errors: [{ message: 'No definition selected', code: 'NO_DEFINITION' }] }
      return
    }

    status.value = mode.value === 'run' ? 'running' : 'loading'
    output.value = null

    const api = useDslApi()
    try {
      const payload = formData.value
      let response: unknown
      if (mode.value === 'preview') {
        response = await api.preview(name, payload)
      }
      else if (mode.value === 'run') {
        response = await api.run(name, payload)
      }
      else {
        response = await api.explain(name, payload)
      }

      output.value = normalizeResponse(response)
      status.value = 'success'
    }
    catch (err: unknown) {
      output.value = errorToOutput(err)
      status.value = 'failed'
    }
  }

  async function confirmRun() {
    if (mode.value === 'run') {
      showConfirmModal.value = true
      return
    }
    await submit()
  }

  function normalizeResponse(response: unknown): RunnerOutput {
    if (response && typeof response === 'object') {
      const r = response as Record<string, unknown>
      return {
        result: r.result ?? r.body ?? r.output,
        metadata: asRecord(r.metadata),
        errors: asErrors(r.errors),
        mermaidDiagram: typeof r.mermaidDiagram === 'string' ? r.mermaidDiagram : undefined,
        description: typeof r.description === 'string' ? r.description : undefined,
        executionTrace: asStringArray(r.executionTrace),
        workflowId: typeof r.workflowId === 'string' ? r.workflowId : undefined,
      }
    }
    return { result: response }
  }

  function errorToOutput(err: unknown): RunnerOutput {
    const fetchErr = err as { data?: { message?: string; errors?: RunnerOutput['errors'] }, message?: string, statusMessage?: string }
    const message = fetchErr?.data?.message
      ?? fetchErr?.statusMessage
      ?? fetchErr?.message
      ?? 'Request failed'
    return {
      errors: fetchErr?.data?.errors ?? [{ message, code: 'REQUEST_FAILED' }],
    }
  }

  function asRecord(v: unknown): Record<string, unknown> | undefined {
    if (v && typeof v === 'object' && !Array.isArray(v)) return v as Record<string, unknown>
    return undefined
  }

  function asErrors(v: unknown): RunnerOutput['errors'] {
    if (!Array.isArray(v)) return undefined
    return v
      .filter(e => e && typeof e === 'object')
      .map(e => {
        const err = e as { message?: string, code?: string }
        return {
          message: typeof err.message === 'string' ? err.message : 'Unknown error',
          code: typeof err.code === 'string' ? err.code : undefined,
        }
      })
  }

  function asStringArray(v: unknown): string[] | undefined {
    if (!Array.isArray(v)) return undefined
    return v.filter(x => typeof x === 'string') as string[]
  }

  return {
    selectedDefinition,
    mode,
    status,
    formData,
    output,
    showConfirmModal,
    selectDefinition,
    setMode,
    submit,
    confirmRun,
    resetOutput,
  }
}