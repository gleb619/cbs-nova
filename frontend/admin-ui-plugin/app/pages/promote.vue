<script setup lang="ts">
import { usePromotion } from '@cbs/admin-ui-plugin/composables/usePromotion'
import { DslPromotionDiffTable } from '@cbs/components'
import { computed, onMounted, ref } from 'vue'

const {
  environments,
  environmentsLoading,
  environmentsError,
  definitions,
  definitionsLoading,
  definitionsError,
  preview,
  previewLoading,
  previewError,
  applying,
  applyError,
  applyResult,
  loadEnvironments,
  loadDefinitions,
  runPreview,
  apply,
  reset,
} = usePromotion()

const source = ref('')
const target = ref('')
const selected = ref<Set<string>>(new Set())
const includeDrafts = ref(false)

onMounted(() => {
  void loadEnvironments()
})

const selectableTargets = computed(() =>
  environments.value.filter((env) => env.name !== source.value),
)

async function onSourceChange() {
  selected.value = new Set()
  reset()
  if (!source.value) {
    return
  }
  await loadDefinitions(source.value)
}

function onTargetChange() {
  reset()
}

function toggleDefinition(name: string) {
  const next = new Set(selected.value)
  if (next.has(name)) {
    next.delete(name)
  } else {
    next.add(name)
  }
  selected.value = next
  reset()
}

function toggleAll() {
  selected.value =
    selected.value.size === definitions.value.length
      ? new Set()
      : new Set(definitions.value.map((d) => d.name))
  reset()
}

const payload = computed(() => ({
  source: source.value,
  target: target.value,
  definitions: selected.value.size > 0 ? [...selected.value] : undefined,
  includeDrafts: includeDrafts.value || undefined,
}))

const canPreview = computed(
  () =>
    source.value !== '' &&
    target.value !== '' &&
    !previewLoading.value &&
    !applying.value,
)

async function onPreview() {
  await runPreview(payload.value)
}

const changesCount = computed(
  () =>
    preview.value?.results.filter(
      (r) => r.outcome === 'created' || r.outcome === 'updated',
    ).length ?? 0,
)

async function onApply() {
  await apply(payload.value)
}

const auditUrl = computed(() => '/api/v1/dsl/audit?action=PROMOTION')
</script>

<template>
  <div class="p-6 space-y-4 h-full flex flex-col" data-testid="promote-page">
    <header>
      <h1 class="text-2xl font-bold text-neutral-900">Promote</h1>
      <p class="text-sm text-neutral-600">
        Move published definition bundles between environments — preview the diff, then apply.
      </p>
    </header>

    <div class="flex flex-wrap items-end gap-4">
      <label class="flex flex-col gap-1">
        <span class="text-xs font-semibold text-neutral-600">Source environment</span>
        <select
          v-model="source"
          class="rounded-md border border-neutral-300 px-3 py-2 text-sm"
          data-testid="promote-source-select"
          @change="onSourceChange"
        >
          <option value="" disabled>Select source</option>
          <option v-for="env in environments" :key="env.name" :value="env.name">
            {{ env.name }}
          </option>
        </select>
      </label>
      <label class="flex flex-col gap-1">
        <span class="text-xs font-semibold text-neutral-600">Target environment</span>
        <select
          v-model="target"
          class="rounded-md border border-neutral-300 px-3 py-2 text-sm"
          data-testid="promote-target-select"
          @change="onTargetChange"
        >
          <option value="" disabled>Select target</option>
          <option v-for="env in selectableTargets" :key="env.name" :value="env.name">
            {{ env.name }}
          </option>
        </select>
      </label>
      <label class="flex items-center gap-2 pb-2 text-sm text-neutral-700">
        <input
          v-model="includeDrafts"
          type="checkbox"
          data-testid="promote-include-drafts"
          @change="reset"
        />
        Include drafts
      </label>
    </div>

    <p v-if="environmentsLoading" class="text-sm text-neutral-500">Loading environments…</p>
    <p v-else-if="environmentsError" class="text-sm text-error-700" data-testid="promote-environments-error">
      {{ environmentsError }}
    </p>
    <p
      v-else-if="environments.length === 0"
      class="text-sm text-neutral-500"
      data-testid="promote-no-environments"
    >
      No promotion environments configured. Set
      <code>cbs.dsl.promotion.environments.&lt;name&gt;.base-path</code> on the backend.
    </p>

    <div v-if="source" class="grid grid-cols-1 gap-4 lg:grid-cols-2">
      <section class="rounded-lg border border-neutral-200 bg-white p-4">
        <div class="mb-2 flex items-center justify-between">
          <h2 class="text-sm font-semibold text-neutral-900">Definitions in {{ source }}</h2>
          <button
            type="button"
            class="text-xs font-semibold text-primary-600"
            data-testid="promote-toggle-all"
            @click="toggleAll"
          >
            {{ selected.size === definitions.length ? 'Clear all' : 'Select all' }}
          </button>
        </div>
        <p v-if="definitionsLoading" class="text-sm text-neutral-500">Loading definitions…</p>
        <p v-else-if="definitionsError" class="text-sm text-error-700" data-testid="promote-definitions-error">
          {{ definitionsError }}
        </p>
        <p v-else-if="definitions.length === 0" class="text-sm text-neutral-500">
          No published definitions in {{ source }}.
        </p>
        <ul v-else class="max-h-72 divide-y divide-neutral-100 overflow-y-auto">
          <li v-for="def in definitions" :key="def.name">
            <label
              class="flex cursor-pointer items-center gap-2 px-1 py-1.5 text-sm text-neutral-800"
              :data-testid="`promote-def-${def.name}`"
            >
              <input
                type="checkbox"
                :checked="selected.has(def.name)"
                @change="toggleDefinition(def.name)"
              />
              <span>{{ def.name }}</span>
              <span class="text-xs text-neutral-500">{{ def.type }} · {{ def.status }}</span>
            </label>
          </li>
        </ul>
        <p class="mt-2 text-xs text-neutral-500">
          {{ selected.size === 0 ? 'All definitions will be promoted.' : `${selected.size} selected.` }}
        </p>
      </section>

      <section class="rounded-lg border border-neutral-200 bg-white p-4">
        <div class="mb-2 flex items-center justify-between">
          <h2 class="text-sm font-semibold text-neutral-900">Preview</h2>
          <button
            type="button"
            class="rounded-md bg-primary-600 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
            :disabled="!canPreview"
            data-testid="promote-preview-button"
            @click="onPreview"
          >
            {{ previewLoading ? 'Previewing…' : 'Preview diff' }}
          </button>
        </div>
        <p v-if="previewError" class="text-sm text-error-700" data-testid="promote-preview-error">
          {{ previewError }}
        </p>
        <template v-else-if="preview">
          <p class="mb-2 text-sm text-neutral-700" data-testid="promote-preview-summary">
            {{ changesCount }} of {{ preview.results.length }} definitions would change on
            {{ target }}.
          </p>
          <DslPromotionDiffTable :results="preview.results" />
          <button
            type="button"
            class="mt-3 rounded-md bg-success-600 px-3 py-1.5 text-xs font-semibold text-white disabled:opacity-50"
            :disabled="applying || changesCount === 0"
            data-testid="promote-apply-button"
            @click="onApply"
          >
            {{ applying ? 'Promoting…' : `Promote to ${target}` }}
          </button>
        </template>
        <p v-else class="text-sm text-neutral-500" data-testid="promote-preview-placeholder">
          Pick a source and target, then preview the diff before applying.
        </p>
      </section>
    </div>

    <div
      v-if="applyResult"
      class="rounded-lg border border-success-200 bg-success-50 p-4"
      data-testid="promote-apply-result"
    >
      <p class="text-sm font-semibold text-success-800">
        Promoted {{ applyResult.published }} definition(s) to {{ target }}.
      </p>
      <p v-if="applyResult.failed > 0" class="text-sm text-error-700">
        {{ applyResult.failed }} definition(s) failed.
      </p>
      <DslPromotionDiffTable :results="applyResult.results" class="mt-2" />
      <a
        :href="auditUrl"
        class="mt-2 inline-block text-xs font-semibold text-primary-600"
        data-testid="promote-audit-link"
      >
        View promotion audit log
      </a>
    </div>
    <p v-if="applyError" class="text-sm text-error-700" data-testid="promote-apply-error">
      {{ applyError }}
    </p>
  </div>
</template>
