import type { RunnerMode, RunnerOutput, RunnerStatus } from '~/types'
import { useDslApi } from './useDslApi'

const selectedDefinition = ref<string | null>(null)
const mode = ref<RunnerMode>('preview')
const status = ref<RunnerStatus>('idle')
const formData = ref<Record<string, unknown>>({})
const mocks = ref<Record<string, Record<string, unknown>>>({})
const output = ref<RunnerOutput | null>(null)
const baselineOutput = ref<RunnerOutput | null>(null)
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
    mocks.value = {}
    baselineOutput.value = null
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
        response = await api.preview(name, payload, undefined, mocks.value)
      } else if (mode.value === 'run') {
        response = await api.run(name, payload)
      } else {
        response = await api.explain(name, payload)
      }

      output.value = normalizeResponse(response)
      status.value = 'success'
    } catch (err: unknown) {
      output.value = errorToOutput(err)
      status.value = 'failed'
    }
  }

  /**
   * Copy the current preview `output` into `baselineOutput` and run a fresh
   * preview so the user can compare the previous result against the new one.
   * No-op when there's nothing to use as a baseline or when not in preview mode.
   */
  async function compareWithPrevious() {
    if (mode.value !== 'preview') return
    if (!output.value) return
    baselineOutput.value = cloneOutput(output.value)
    await submit()
  }

  function clearBaseline() {
    baselineOutput.value = null
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
        astTree: asCallNode(r.astTree),
        dryRunLogs: asDryRunLogs(r.dryRunLogs),
        metrics: asMetrics(r.metrics),
      }
    }
    return { result: response }
  }

  function errorToOutput(err: unknown): RunnerOutput {
    const fetchErr = err as {
      data?: { message?: string; errors?: RunnerOutput['errors'] }
      message?: string
      statusMessage?: string
    }
    const message =
      fetchErr?.data?.message ?? fetchErr?.statusMessage ?? fetchErr?.message ?? 'Request failed'
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
      .filter((e) => e && typeof e === 'object')
      .map((e) => {
        const err = e as { message?: string; code?: string }
        return {
          message: typeof err.message === 'string' ? err.message : 'Unknown error',
          code: typeof err.code === 'string' ? err.code : undefined,
        }
      })
  }

  function asStringArray(v: unknown): string[] | undefined {
    if (!Array.isArray(v)) return undefined
    return v.filter((x) => typeof x === 'string') as string[]
  }

  function asCallNode(v: unknown): RunnerOutput['astTree'] {
    if (!v || typeof v !== 'object' || Array.isArray(v)) return undefined
    const node = v as Record<string, unknown>
    const kind = node.kind
    if (kind !== 'PROCESS' && kind !== 'TRANSACTION' && kind !== 'HELPER' && kind !== 'FUNCTION') {
      return undefined
    }
    const children = Array.isArray(node.children)
      ? (node.children.map((c) => asCallNode(c)).filter(Boolean) as RunnerOutput['astTree'][])
      : []
    const externalCalls = Array.isArray(node.externalCalls)
      ? (node.externalCalls.filter((c) => c && typeof c === 'object') as Array<Record<string, unknown>>)
      : []
    return {
      name: typeof node.name === 'string' ? node.name : '',
      kind,
      success: node.success === true,
      children,
      externalCalls,
      input: node.input,
      output: node.output,
    }
  }

  function asDryRunLogs(v: unknown): RunnerOutput['dryRunLogs'] {
    if (!Array.isArray(v)) return undefined
    return v
      .filter((e) => e && typeof e === 'object')
      .map((e) => {
        const log = e as { timestamp?: unknown; level?: unknown; logger?: unknown; message?: unknown }
        return {
          timestamp: typeof log.timestamp === 'string' ? log.timestamp : '',
          level: typeof log.level === 'string' ? log.level : '',
          logger: typeof log.logger === 'string' ? log.logger : '',
          message: typeof log.message === 'string' ? log.message : '',
        }
      })
  }

  function asMetrics(v: unknown): RunnerOutput['metrics'] {
    if (!v || typeof v !== 'object' || Array.isArray(v)) return undefined
    const m = v as Record<string, unknown>
    return {
      executionDurationMs:
        typeof m.executionDurationMs === 'number' && Number.isFinite(m.executionDurationMs)
          ? m.executionDurationMs
          : null,
      memoryUsedBytes:
        typeof m.memoryUsedBytes === 'number' && Number.isFinite(m.memoryUsedBytes) ? m.memoryUsedBytes : null,
      callCounts: asNumericMap(m.callCounts),
      externalCallCounts: asNumericMap(m.externalCallCounts),
    }
  }

  function asNumericMap(v: unknown): Record<string, number> | undefined {
    if (!v || typeof v !== 'object' || Array.isArray(v)) return undefined
    const out: Record<string, number> = {}
    for (const [k, val] of Object.entries(v as Record<string, unknown>)) {
      if (typeof val === 'number' && Number.isFinite(val)) out[k] = val
    }
    return out
  }

  function cloneOutput(value: RunnerOutput): RunnerOutput {
    // `structuredClone` is available in Node 17+ and modern browsers; the
    // admin UI targets the same environment, so a deep snapshot is safe and
    // guarantees the baseline isn't mutated by the next preview run.
    if (typeof structuredClone === 'function') {
      return structuredClone(value)
    }
    return JSON.parse(JSON.stringify(value)) as RunnerOutput
  }

  return {
    selectedDefinition,
    mode,
    status,
    formData,
    mocks,
    output,
    baselineOutput,
    showConfirmModal,
    selectDefinition,
    setMode,
    submit,
    confirmRun,
    resetOutput,
    compareWithPrevious,
    clearBaseline,
  }
}
