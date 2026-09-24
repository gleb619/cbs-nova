<script setup lang="ts">
import type { DslTemplate } from '@cbs/components'
import { computed } from 'vue'
import DslTemplateGallery from '../DslTemplateGallery.vue'

// New-definition modal: name input + template gallery + Create/Cancel footer.
//
// Decision: extracted from `dsl-workbench.vue` so the page stays a thin
// orchestrator. The component is purely presentational — it surfaces the
// validation error, gates the Create button, and emits the desired
// {name, body} pair upward. The page owns the workbench.createConstruct +
// draft-body seed + markDirty wiring.

const props = defineProps<{
  open: boolean
  newName: string
  selectedTemplate: DslTemplate | null
  newNameError: string | null
}>()

const emit = defineEmits<{
  (event: 'update:new-name', value: string): void
  (event: 'select-template', template: DslTemplate): void
  (event: 'cancel'): void
  (event: 'create'): void
}>()

const canCreate = computed(
  () => !!props.newName.trim() && !props.newNameError && !!props.selectedTemplate,
)

const subtitle = 'Choose a starter template and name for the new DSL definition.'
</script>

<template>
  <!-- biome-ignore lint/a11y/useKeyWithClickEvents: backdrop click dismisses modal -->
  <div
    v-if="open"
    class="fixed inset-0 z-50 bg-black/50 flex items-center justify-center p-4"
    role="dialog"
    aria-modal="true"
    aria-labelledby="workbench-new-title"
    @click.self="emit('cancel')"
  >
    <div class="bg-white rounded-xl shadow-xl max-w-2xl w-full flex flex-col max-h-[90vh]">
      <header class="px-6 py-4 border-b border-line">
        <h2 id="workbench-new-title" class="text-lg font-semibold text-ink">New definition</h2>
        <p class="text-sm text-ink-muted mt-1">
          {{ subtitle }}
        </p>
      </header>

      <div class="px-6 py-4 overflow-y-auto">
        <div class="mb-4">
          <label for="workbench-new-name" class="block text-sm font-medium text-ink mb-1">
            Name
          </label>
          <input
            id="workbench-new-name"
            :value="newName"
            type="text"
            class="w-full px-3 py-2 border border-line rounded focus:outline-none focus:ring-2 focus:ring-accent-500"
            placeholder="Definition name"
            data-testid="workbench-new-name"
            @input="emit('update:new-name', ($event.target as HTMLInputElement).value)"
          >
          <p
            v-if="newNameError"
            class="mt-1 text-xs text-danger"
            data-testid="workbench-new-name-error"
          >
            {{ newNameError }}
          </p>
        </div>

        <DslTemplateGallery
          @select="(template: DslTemplate) => emit('select-template', template)"
        />
      </div>

      <footer class="px-6 py-4 border-t border-line flex justify-end gap-2">
        <button
          type="button"
          class="px-4 py-2 rounded-lg text-sm font-medium border border-line text-ink hover:bg-surface"
          data-testid="workbench-new-cancel"
          @click="emit('cancel')"
        >
          Cancel
        </button>
        <button
          type="button"
          class="px-4 py-2 rounded-lg text-sm font-medium text-white"
          :class="canCreate
              ? 'bg-accent-500 hover:bg-accent-600'
              : 'bg-accent-500/20 cursor-not-allowed'"
          :disabled="!canCreate"
          data-testid="workbench-new-create"
          @click="emit('create')"
        >
          Create
        </button>
      </footer>
    </div>
  </div>
</template>
