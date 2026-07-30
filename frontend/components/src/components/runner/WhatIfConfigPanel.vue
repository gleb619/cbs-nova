<script setup lang="ts">
type MockValue = Record<string, unknown>
type MockMap = Record<string, MockValue>

const props = defineProps<{
  modelValue: MockMap
}>()

const emit = defineEmits<{
  'update:modelValue': [data: MockMap]
}>()

interface Row {
  id: number
  signature: string
  payloadText: string
  error: string | null
}

let nextId = 1

function buildRow(signature: string, payload: MockValue | undefined): Row {
  return {
    id: nextId++,
    signature,
    payloadText:
      payload && Object.keys(payload).length > 0 ? JSON.stringify(payload, null, 2) : '',
    error: null,
  }
}

function makeEmptyRow(): Row {
  return { id: nextId++, signature: '', payloadText: '', error: null }
}

const rows = ref<Row[]>([])

function modelFromRows(): MockMap {
  const out: MockMap = {}
  for (const row of rows.value) {
    const sig = row.signature.trim()
    if (!sig) continue
    let payload: MockValue = {}
    const txt = row.payloadText.trim()
    if (txt) {
      try {
        const parsed = JSON.parse(txt)
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
          payload = parsed as MockValue
        } else {
          continue
        }
      } catch {
        continue
      }
    }
    if (!(sig in out)) out[sig] = payload
  }
  return out
}

function modelsEqual(a: MockMap, b: MockMap): boolean {
  const ak = Object.keys(a)
  const bk = Object.keys(b)
  if (ak.length !== bk.length) return false
  for (const k of ak) {
    if (!(k in b)) return false
    if (JSON.stringify(a[k]) !== JSON.stringify(b[k])) return false
  }
  return true
}

watch(
  () => props.modelValue,
  (next) => {
    const incoming = next ?? {}
    if (modelsEqual(modelFromRows(), incoming)) return
    const entries = Object.entries(incoming)
    rows.value = entries.length === 0 ? [] : entries.map(([sig, val]) => buildRow(sig, val))
  },
  { immediate: true },
)

function rebuildModel() {
  const out: MockMap = {}
  for (const row of rows.value) {
    const sig = row.signature.trim()
    if (!sig) {
      row.error = null
      continue
    }
    let payload: MockValue = {}
    const txt = row.payloadText.trim()
    if (txt) {
      try {
        const parsed = JSON.parse(txt)
        if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) {
          payload = parsed as MockValue
          row.error = null
        } else {
          row.error = 'Payload must be a JSON object'
          continue
        }
      } catch (err) {
        row.error = (err as Error).message
        continue
      }
    } else {
      row.error = null
    }
    if (!(sig in out)) out[sig] = payload
  }
  emit('update:modelValue', out)
}

function addRow() {
  rows.value = [...rows.value, makeEmptyRow()]
  rebuildModel()
}

function removeRow(id: number) {
  rows.value = rows.value.filter((r) => r.id !== id)
  rebuildModel()
}

function updateSignature(id: number, value: string) {
  rows.value = rows.value.map((r) => (r.id === id ? { ...r, signature: value } : r))
  rebuildModel()
}

function updatePayload(id: number, value: string) {
  rows.value = rows.value.map((r) => (r.id === id ? { ...r, payloadText: value } : r))
  rebuildModel()
}
</script>

<template>
  <div class="flex flex-col gap-3">
    <div v-if="rows.length === 0" class="text-sm text-gray-500">
      No mock entries. Add one to short-circuit external calls during preview.
    </div>

    <ul v-else class="flex flex-col gap-3">
      <li
        v-for="row in rows"
        :key="row.id"
        class="border border-gray-200 rounded-lg p-3 flex flex-col gap-2"
      >
        <div class="flex items-start gap-2">
          <label class="flex-1 flex flex-col gap-1 text-sm">
            <span class="text-xs font-medium text-gray-600">
              Call signature (type:target:operation)
            </span>
            <input
              type="text"
              :value="row.signature"
              class="px-3 py-2 border border-gray-300 rounded-lg text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
              placeholder="activity:MyActivity:invoke"
              @input="(e) => updateSignature(row.id, (e.target as HTMLInputElement).value)"
            />
          </label>
          <button
            type="button"
            class="mt-6 text-xs font-medium text-red-600 hover:text-red-700 px-2 py-1 rounded border border-red-200 hover:bg-red-50 transition-colors"
            @click="removeRow(row.id)"
          >
            Remove
          </button>
        </div>
        <label class="flex flex-col gap-1 text-sm">
          <span class="text-xs font-medium text-gray-600">Mock payload (JSON)</span>
          <textarea
            :value="row.payloadText"
            rows="4"
            class="px-3 py-2 border rounded-lg text-sm font-mono focus:outline-none focus:ring-2 focus:ring-blue-500"
            :class="row.error ? 'border-red-400' : 'border-gray-300'"
            placeholder='{ "result": "mocked" }'
            @input="(e) => updatePayload(row.id, (e.target as HTMLTextAreaElement).value)"
          />
          <span v-if="row.error" class="text-xs text-red-600"
            >Invalid JSON: {{ row.error }}</span
          >
        </label>
      </li>
    </ul>

    <button
      type="button"
      class="self-start text-sm font-medium text-blue-600 hover:text-blue-700 px-3 py-1.5 rounded border border-blue-200 hover:bg-blue-50 transition-colors"
      @click="addRow"
    >
      Add mock
    </button>
  </div>
</template>