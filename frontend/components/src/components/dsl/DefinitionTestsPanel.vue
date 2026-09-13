<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useDiffLines } from '../../composables/useDiffLines'
import type {
  DefinitionTestCase,
  DefinitionTestCaseResult,
  DefinitionTestRunReport,
  DefinitionTestStatus,
} from '../../types/dsl'
import ErrorBanner from '../ErrorBanner.vue'
import RunnerDiffLine from '../runner/DiffLine.vue'

interface EditableCase {
  key: number
  caseName: string
  inputText: string
  expectedText: string
}

const props = defineProps<{
  name: string
  fetchTests: (name: string) => Promise<DefinitionTestCase[]>
  saveTests: (name: string, cases: DefinitionTestCase[]) => Promise<unknown>
  runTests: (name: string, caseNames?: string[]) => Promise<DefinitionTestRunReport>
}>()

let nextKey = 1

const cases = ref<EditableCase[]>([])
const loading = ref(false)
const loadError = ref<string | null>(null)

const saving = ref(false)
const saveError = ref<string | null>(null)
const savedBaseline = ref('[]')

const running = ref(false)
const runError = ref<string | null>(null)
const report = ref<DefinitionTestRunReport | null>(null)

const selectedNames = ref<string[]>([])
const expandedName = ref<string | null>(null)

function formatJson(value: unknown): string {
  if (value === undefined || value === null) return ''
  return JSON.stringify(value, null, 2)
}

function toEditable(test: DefinitionTestCase): EditableCase {
  return {
    key: nextKey++,
    caseName: test.caseName,
    inputText: formatJson(test.input),
    expectedText: formatJson(test.expectedOutput),
  }
}

function snapshot(rows: EditableCase[]): string {
  return JSON.stringify(rows.map((row) => [row.caseName, row.inputText, row.expectedText]))
}

function jsonError(text: string): string | null {
  try {
    JSON.parse(text)
    return null
  } catch (err) {
    return (err as Error).message
  }
}

const rowErrors = computed(() =>
  cases.value.map((row) => ({
    input: jsonError(row.inputText),
    expected: jsonError(row.expectedText),
  })),
)
const hasInvalidJson = computed(() =>
  rowErrors.value.some((errors) => errors.input !== null || errors.expected !== null),
)
const hasUnnamedCases = computed(() => cases.value.some((row) => !row.caseName.trim()))
const isDirty = computed(() => snapshot(cases.value) !== savedBaseline.value)
const canSave = computed(
  () => isDirty.value && !hasInvalidJson.value && !hasUnnamedCases.value && !saving.value,
)
const canRun = computed(() => !running.value && cases.value.length > 0)
const canRunSelected = computed(() => canRun.value && selectedNames.value.length > 0)

const resultByName = computed(() => {
  const map = new Map<string, DefinitionTestCaseResult>()
  for (const result of report.value?.cases ?? []) map.set(result.name, result)
  return map
})

const expandedResult = computed(() => {
  if (!expandedName.value) return null
  return resultByName.value.get(expandedName.value) ?? null
})
const expandedExpected = computed(() => formatJson(expandedResult.value?.expected))
const expandedActual = computed(() => formatJson(expandedResult.value?.actual))
const expandedDiffLines = useDiffLines(expandedExpected, expandedActual)

const statusStyles: Record<DefinitionTestStatus, string> = {
  PASS: 'bg-green-100 text-green-800',
  FAIL: 'bg-yellow-100 text-yellow-800',
  ERROR: 'bg-red-100 text-red-800',
}

function statusClass(status: string): string {
  return statusStyles[status as DefinitionTestStatus] ?? 'bg-gray-100 text-gray-800'
}

// biome-ignore lint/correctness/noUnusedVariables: used in the template
function formatDiagnostics(diagnostics: unknown): string {
  if (diagnostics === undefined || diagnostics === null) return ''
  if (typeof diagnostics === 'string') return diagnostics
  try {
    return JSON.stringify(diagnostics, null, 2)
  } catch {
    return String(diagnostics)
  }
}

async function load(): Promise<void> {
  if (!props.name) {
    cases.value = []
    return
  }
  loading.value = true
  loadError.value = null
  try {
    const tests = await props.fetchTests(props.name)
    cases.value = tests.map(toEditable)
    savedBaseline.value = snapshot(cases.value)
    selectedNames.value = []
  } catch (err) {
    loadError.value = (err as Error).message
    cases.value = []
  } finally {
    loading.value = false
  }
}

function addCase(): void {
  cases.value.push({ key: nextKey++, caseName: '', inputText: '', expectedText: '' })
}

function removeCase(index: number): void {
  const removed = cases.value[index]
  if (!removed) return
  selectedNames.value = selectedNames.value.filter((entry) => entry !== removed.caseName)
  if (expandedName.value === removed.caseName) expandedName.value = null
  cases.value.splice(index, 1)
}

function toPayload(): DefinitionTestCase[] {
  return cases.value.map((row) => ({
    caseName: row.caseName.trim(),
    input: JSON.parse(row.inputText),
    expectedOutput: JSON.parse(row.expectedText),
  }))
}

async function save(): Promise<void> {
  if (!canSave.value) return
  saving.value = true
  saveError.value = null
  try {
    await props.saveTests(props.name, toPayload())
    savedBaseline.value = snapshot(cases.value)
  } catch (err) {
    saveError.value = (err as Error).message
  } finally {
    saving.value = false
  }
}

async function run(caseNames?: string[]): Promise<void> {
  if (running.value || !props.name) return
  running.value = true
  runError.value = null
  try {
    report.value = await props.runTests(props.name, caseNames?.length ? caseNames : undefined)
    expandedName.value = null
  } catch (err) {
    runError.value = (err as Error).message
  } finally {
    running.value = false
  }
}

function runAll(): void {
  void run()
}

function runSelected(): void {
  void run([...selectedNames.value])
}

function isSelected(name: string): boolean {
  return selectedNames.value.includes(name)
}

function toggleSelected(name: string): void {
  selectedNames.value = isSelected(name)
    ? selectedNames.value.filter((entry) => entry !== name)
    : [...selectedNames.value, name]
}

function toggleExpanded(name: string): void {
  expandedName.value = expandedName.value === name ? null : name
}

// Like DslHistoryPanel: the parent remounts this panel (via `:key`) whenever
// the construct name changes, so mounting is the only load moment.
onMounted(() => {
  void load()
})
</script>

<template>
  <div class="flex flex-col h-full" data-testid="tests-panel">
    <div class="px-4 py-3 border-b border-gray-200">
      <h2 class="text-base font-semibold text-gray-900">Test cases</h2>
      <p class="text-xs text-gray-500 mt-0.5">
        Author-defined cases stored for this construct — edit the set, save it, then run it to get a
        PASS / FAIL / ERROR report.
      </p>
    </div>

    <div class="px-4 py-2 border-b border-gray-200 flex flex-wrap items-center gap-2">
      <button
        type="button"
        class="px-3 py-1.5 text-sm rounded border border-gray-300 hover:bg-gray-100"
        data-testid="tests-add-case"
        @click="addCase"
      >
        Add case
      </button>
      <button
        type="button"
        class="px-3 py-1.5 text-sm rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
        :disabled="!canSave"
        data-testid="tests-save-button"
        @click="save"
      >
        {{ saving ? 'Saving…' : 'Save' }}
      </button>
      <button
        type="button"
        class="px-3 py-1.5 text-sm rounded text-white bg-blue-600 hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed"
        :disabled="!canRun"
        data-testid="tests-run-all"
        @click="runAll"
      >
        Run all
      </button>
      <button
        type="button"
        class="px-3 py-1.5 text-sm rounded border border-gray-300 hover:bg-gray-100 disabled:opacity-50 disabled:cursor-not-allowed"
        :disabled="!canRunSelected"
        data-testid="tests-run-selected"
        @click="runSelected"
      >
        Run selected
      </button>
      <span
        v-if="running"
        class="inline-flex items-center gap-1 text-xs text-gray-500"
        data-testid="tests-running"
      >
        <span
          class="inline-block w-3 h-3 rounded-full border-2 border-gray-400 border-t-transparent animate-spin"
        />
        Running…
      </span>
      <span v-if="isDirty" class="text-xs text-yellow-700" data-testid="tests-unsaved-hint">
        Unsaved changes
      </span>
    </div>

    <div class="flex-1 overflow-y-auto">
      <div v-if="loading" class="px-4 py-3 text-sm text-gray-500" data-testid="tests-loading">
        Loading test cases…
      </div>
      <div v-else-if="loadError" class="px-4 py-3" data-testid="tests-error">
        <ErrorBanner :message="loadError" retry-label="Retry" @retry="load" />
      </div>
      <div
        v-else-if="!name || cases.length === 0"
        class="px-4 py-3 text-sm text-gray-500"
        data-testid="tests-empty"
      >
        No test cases yet — add one.
      </div>

      <ul v-else class="divide-y divide-gray-100" data-testid="tests-case-list">
        <li
          v-for="(row, index) in cases"
          :key="row.key"
          class="px-4 py-3"
          data-testid="tests-case-row"
        >
          <div class="flex items-center gap-2">
            <input
              type="checkbox"
              :checked="isSelected(row.caseName)"
              data-testid="tests-case-select"
              @change="toggleSelected(row.caseName)"
            >
            <input
              v-model="row.caseName"
              type="text"
              placeholder="Case name"
              class="flex-1 px-2 py-1 text-sm border border-gray-300 rounded"
              data-testid="tests-case-name"
            >
            <button
              type="button"
              class="px-2 py-1 text-sm rounded border border-gray-300 hover:bg-gray-100"
              data-testid="tests-case-remove"
              @click="removeCase(index)"
            >
              Remove
            </button>
          </div>
          <div class="grid grid-cols-1 md:grid-cols-2 gap-2 mt-2">
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">
                Input (JSON)
                <textarea
                  v-model="row.inputText"
                  spellcheck="false"
                  rows="6"
                  class="w-full p-2 mt-1 font-mono text-xs border border-gray-300 rounded resize-y"
                  data-testid="tests-case-input"
                />
              </label>
              <p
                v-if="rowErrors[index]?.input"
                class="mt-1 text-xs text-red-600"
                data-testid="tests-case-input-error"
              >
                Invalid JSON: {{ rowErrors[index]?.input }}
              </p>
            </div>
            <div>
              <label class="block text-xs font-medium text-gray-500 mb-1">
                Expected output (JSON)
                <textarea
                  v-model="row.expectedText"
                  spellcheck="false"
                  rows="6"
                  class="w-full p-2 mt-1 font-mono text-xs border border-gray-300 rounded resize-y"
                  data-testid="tests-case-expected"
                />
              </label>
              <p
                v-if="rowErrors[index]?.expected"
                class="mt-1 text-xs text-red-600"
                data-testid="tests-case-expected-error"
              >
                Invalid JSON: {{ rowErrors[index]?.expected }}
              </p>
            </div>
          </div>
        </li>
      </ul>

      <div v-if="saveError" class="px-4 py-2 text-sm text-red-600" data-testid="tests-save-error">
        {{ saveError }}
      </div>
      <div v-if="runError" class="px-4 py-2 text-sm text-red-600" data-testid="tests-run-error">
        {{ runError }}
      </div>

      <div v-if="report" class="border-t border-gray-200 px-4 py-3" data-testid="tests-report">
        <h3 class="text-sm font-semibold text-gray-900 mb-1">Run report</h3>
        <p class="text-xs text-gray-600 mb-2" data-testid="tests-report-summary">
          {{ report.passed }}
          passed · {{ report.failed }} failed · {{ report.errored }} errored (of {{ report.total }})
        </p>
        <ul class="divide-y divide-gray-100">
          <li
            v-for="result in report.cases"
            :key="result.name"
            data-testid="tests-result-row"
            :data-status="result.status"
          >
            <button
              type="button"
              class="w-full text-left px-2 py-2 hover:bg-gray-50 flex items-center gap-2"
              data-testid="tests-result-toggle"
              @click="toggleExpanded(result.name)"
            >
              <span
                class="inline-flex items-center px-2 py-0.5 rounded-full text-xs font-medium"
                :class="statusClass(result.status)"
                data-testid="tests-result-badge"
              >
                {{ result.status }}
              </span>
              <span class="text-sm font-medium text-gray-900">{{ result.name }}</span>
              <span class="text-xs text-gray-500" data-testid="tests-result-duration">
                {{ result.durationMs }}
                ms
              </span>
              <span class="ml-auto text-xs text-gray-400">
                {{ expandedName === result.name ? 'Collapse' : 'Expand' }}
              </span>
            </button>
            <div
              v-if="expandedName === result.name"
              class="px-2 pb-3"
              data-testid="tests-result-detail"
            >
              <div
                v-if="result.status === 'ERROR'"
                class="bg-gray-50 border border-gray-200 rounded-lg p-2 overflow-auto max-h-[40vh]"
                data-testid="tests-result-diagnostics"
              >
                <pre class="font-mono text-xs whitespace-pre-wrap break-words text-red-700">{{
                  formatDiagnostics(result.diagnostics)
                }}</pre>
              </div>
              <div
                v-else
                class="bg-gray-50 border border-gray-200 rounded-lg p-2 overflow-auto max-h-[40vh]"
                data-testid="tests-result-diff"
              >
                <RunnerDiffLine
                  v-for="(line, lineIndex) in expandedDiffLines"
                  :key="lineIndex"
                  :kind="line.kind"
                  :text="line.text"
                />
              </div>
            </div>
          </li>
        </ul>
      </div>
    </div>
  </div>
</template>
