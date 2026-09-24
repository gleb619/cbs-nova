<script setup lang="ts">
import type { DslConstruct } from '@cbs/components'
import { DropdownMenu, type DropdownMenuItem, HotkeyTooltip } from '@cbs/components'

// Header-only sub-component: owns the title row + the three HotkeyTooltip
// buttons (New, Actions, Misc) that used to live inline on the page.
//
// Decision: extracted from `dsl-workbench.vue` so the page stays a thin
// orchestrator. All data-testid values, the platform-aware shortcut labels,
// and the explorer-toggle button are preserved exactly.

defineProps<{
  selectedConstruct: DslConstruct | null
  selectedConstructLabel: string
  selectedPendingApproval: boolean
  newShortcut: string
  actionsShortcut: string
  miscShortcut: string
  actionItems: DropdownMenuItem[]
  helpersMenuItems: DropdownMenuItem[]
}>()

const emit = defineEmits<{
  (event: 'toggleExplorer'): void
  (event: 'newDefinition'): void
  (event: 'selectAction', item: DropdownMenuItem): void
  (event: 'selectHelpersMenu', item: DropdownMenuItem): void
}>()

const explorerToggleLabel = 'Toggle explorer'
const titleText = 'DSL Workbench'
</script>

<template>
  <header class="flex items-center px-4 py-2 bg-white border-b border-line">
    <div class="flex items-center gap-3">
      <button
        type="button"
        class="md:hidden p-1.5 rounded hover:bg-surface"
        :aria-label="explorerToggleLabel"
        @click="emit('toggleExplorer')"
      >
        ☰
      </button>
      <h1 class="text-lg font-semibold text-ink">{{ titleText }}</h1>
      <span
        v-if="selectedConstruct"
        class="text-sm text-ink-muted"
        data-testid="workbench-selected-construct-label"
        :title="selectedConstruct.filePath ?? selectedConstruct.name"
      >
        / {{ selectedConstructLabel }}
      </span>
      <span
        v-if="selectedPendingApproval"
        class="px-2 py-0.5 text-xs font-medium rounded-full border border-warning-300 bg-warning-100 text-warning-800"
        data-testid="workbench-pending-approval"
      >
        Pending approval
      </span>
    </div>
    <div class="ml-auto flex items-center gap-3">
      <HotkeyTooltip :keys="newShortcut" data-testid="workbench-hotkey-new">
        <button
          type="button"
          class="px-3 py-1.5 text-sm rounded border border-line hover:bg-surface"
          data-testid="workbench-new-definition"
          @click="emit('newDefinition')"
        >
          New
        </button>
      </HotkeyTooltip>
      <HotkeyTooltip :keys="actionsShortcut" data-testid="workbench-hotkey-actions">
        <DropdownMenu
          label="Actions"
          align="right"
          :items="actionItems"
          @select="(item: DropdownMenuItem) => emit('selectAction', item)"
        />
      </HotkeyTooltip>
      <HotkeyTooltip :keys="miscShortcut" data-testid="workbench-hotkey-misc">
        <DropdownMenu
          label="Misc"
          align="right"
          :items="helpersMenuItems"
          @select="(item: DropdownMenuItem) => emit('selectHelpersMenu', item)"
        />
      </HotkeyTooltip>
    </div>
  </header>
</template>
