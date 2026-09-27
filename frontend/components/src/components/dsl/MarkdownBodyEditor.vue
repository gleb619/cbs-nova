<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import {
  applyInlineMarker,
  type Block,
  type BlockKind,
  createBlock,
  parseBlocks,
  serializeBlocks,
} from '../../utils/markdownBlocks'

const props = withDefaults(
  defineProps<{
    body: string
    readOnly?: boolean
  }>(),
  { readOnly: false },
)

const emit = defineEmits<{
  'update:body': [value: string]
}>()

const blocks = ref<Block[]>(parseBlocks(props.body))
const focusedBlockId = ref<string | null>(null)

watch(
  () => props.body,
  (next) => {
    const incoming = parseBlocks(next)
    const currentSerialized = serializeBlocks(blocks.value)
    if (currentSerialized !== serializeBlocks(incoming)) {
      blocks.value = incoming
    }
  },
)

const isEmpty = computed(() => blocks.value.length === 0)

function emitUpdate() {
  emit('update:body', serializeBlocks(blocks.value))
}

function emitAsync() {
  nextTick(emitUpdate)
}

function insertBlock(kind: BlockKind, atIndex?: number) {
  if (props.readOnly) return
  const block = createBlock(kind)
  const idx = atIndex ?? focusedInsertionIndex()
  blocks.value.splice(idx, 0, block)
  focusedBlockId.value = block.id
  emitAsync()
}

function focusedInsertionIndex(): number {
  if (focusedBlockId.value === null) return blocks.value.length
  const idx = blocks.value.findIndex((b) => b.id === focusedBlockId.value)
  return idx === -1 ? blocks.value.length : idx + 1
}

function removeBlock(id: string) {
  if (props.readOnly) return
  blocks.value = blocks.value.filter((b) => b.id !== id)
  emitAsync()
}

function moveBlock(id: string, direction: -1 | 1) {
  if (props.readOnly) return
  const idx = blocks.value.findIndex((b) => b.id === id)
  if (idx === -1) return
  const target = idx + direction
  if (target < 0 || target >= blocks.value.length) return
  const next = blocks.value.slice()
  const [moved] = next.splice(idx, 1)
  next.splice(target, 0, moved)
  blocks.value = next
  emitAsync()
}

function setFocused(id: string) {
  focusedBlockId.value = id
}

function _updateBlock(next: Block) {
  const idx = blocks.value.findIndex((b) => b.id === next.id)
  if (idx === -1) return
  const arr = blocks.value.slice()
  arr[idx] = next
  blocks.value = arr
  emitUpdate()
}

function patchBlock(id: string, patch: Partial<Block>) {
  const idx = blocks.value.findIndex((b) => b.id === id)
  if (idx === -1) return
  const arr = blocks.value.slice()
  arr[idx] = { ...arr[idx], ...patch } as Block
  blocks.value = arr
  emitUpdate()
}

function markerTargetId(): string | null {
  if (focusedBlockId.value !== null) {
    const focused = blocks.value.find((b) => b.id === focusedBlockId.value)
    if (
      focused &&
      (focused.kind === 'paragraph' || focused.kind === 'heading' || focused.kind === 'blockquote')
    ) {
      return focused.id
    }
  }
  for (let i = blocks.value.length - 1; i >= 0; i -= 1) {
    const block = blocks.value[i]
    if (block.kind === 'paragraph' || block.kind === 'heading' || block.kind === 'blockquote') {
      return block.id
    }
  }
  return null
}

function applyMarker(marker: 'bold' | 'italic' | 'code' | 'link', linkUrl?: string) {
  if (props.readOnly) return
  const id = markerTargetId()
  if (!id) return
  const block = blocks.value.find((b) => b.id === id)
  if (!block) return
  const text =
    block.kind === 'heading' || block.kind === 'paragraph' || block.kind === 'blockquote'
      ? block.text
      : ''
  const next = applyInlineMarker(text, marker, undefined, linkUrl)
  patchBlock(id, { text: next } as Partial<Block>)
}

function setListItems(id: string, raw: string) {
  const items = raw.split('\n').map((s) => s.replace(/^\s*([-*]|\d+\.)\s+/, ''))
  patchBlock(id, { items } as Partial<Block>)
}

function listText(items: string[]): string {
  return items.join('\n')
}

function addListItem(id: string) {
  const block = blocks.value.find((b) => b.id === id)
  if (block?.kind !== 'list') return
  patchBlock(id, { items: [...block.items, ''] } as Partial<Block>)
}

function removeListItem(id: string, index: number) {
  const block = blocks.value.find((b) => b.id === id)
  if (block?.kind !== 'list') return
  if (block.items.length <= 1) return
  patchBlock(id, { items: block.items.filter((_, i) => i !== index) } as Partial<Block>)
}

function setTableCell(id: string, row: number, col: number, value: string) {
  const block = blocks.value.find((b) => b.id === id)
  if (block?.kind !== 'table') return
  const rows = block.rows.map((r, i) => (i === row ? r.map((c, j) => (j === col ? value : c)) : r))
  patchBlock(id, { rows } as Partial<Block>)
}

function setTableHeader(id: string, col: number, value: string) {
  const block = blocks.value.find((b) => b.id === id)
  if (block?.kind !== 'table') return
  const header = block.header.map((c, i) => (i === col ? value : c))
  patchBlock(id, { header } as Partial<Block>)
}

function addTableRow(id: string) {
  const block = blocks.value.find((b) => b.id === id)
  if (block?.kind !== 'table') return
  const cols = block.header.length
  patchBlock(id, {
    rows: [...block.rows, Array.from({ length: cols }, () => '')],
  } as Partial<Block>)
}

function removeTableRow(id: string, row: number) {
  const block = blocks.value.find((b) => b.id === id)
  if (block?.kind !== 'table') return
  if (block.rows.length <= 1) return
  patchBlock(id, { rows: block.rows.filter((_, i) => i !== row) } as Partial<Block>)
}

function addTableColumn(id: string) {
  const block = blocks.value.find((b) => b.id === id)
  if (block?.kind !== 'table') return
  patchBlock(id, {
    header: [...block.header, ''],
    rows: block.rows.map((r) => [...r, '']),
  } as Partial<Block>)
}

function removeTableColumn(id: string, col: number) {
  const block = blocks.value.find((b) => b.id === id)
  if (block?.kind !== 'table') return
  if (block.header.length <= 1) return
  patchBlock(id, {
    header: block.header.filter((_, i) => i !== col),
    rows: block.rows.map((r) => r.filter((_, i) => i !== col)),
  } as Partial<Block>)
}

function _setBlockType(id: string, kind: BlockKind) {
  if (props.readOnly) return
  const block = blocks.value.find((b) => b.id === id)
  if (!block) return
  const next = createBlock(kind)
  const arr = blocks.value.slice()
  const idx = arr.findIndex((b) => b.id === id)
  arr[idx] = next
  blocks.value = arr
  emitAsync()
}

const INSERT_OPTIONS: { id: BlockKind; label: string; testid: string }[] = [
  { id: 'heading', label: 'H1', testid: 'md-insert-h1' },
  { id: 'heading', label: 'H2', testid: 'md-insert-h2' },
  { id: 'heading', label: 'H3', testid: 'md-insert-h3' },
  { id: 'list', label: '• List', testid: 'md-insert-ul' },
  { id: 'list', label: '1. List', testid: 'md-insert-ol' },
  { id: 'table', label: 'Table', testid: 'md-insert-table' },
  { id: 'code', label: 'Code', testid: 'md-insert-code' },
  { id: 'blockquote', label: 'Quote', testid: 'md-insert-quote' },
  { id: 'hr', label: '— HR', testid: 'md-insert-hr' },
]

function pickInsert(kind: BlockKind) {
  return () => insertBlock(kind)
}

function blockDepth(block: Block): 1 | 2 | 3 {
  return block.kind === 'heading' ? block.depth : 2
}
</script>

<template>
  <div class="flex flex-col gap-2" data-testid="markdown-body-editor">
    <div
      v-if="!readOnly"
      class="flex flex-wrap items-center gap-1 rounded border border-neutral-300 bg-white p-1"
      data-testid="markdown-body-toolbar"
    >
      <div class="flex items-center gap-1 pr-2 border-r border-neutral-200">
        <button
          v-for="opt in INSERT_OPTIONS.filter((o) => o.label.startsWith('H'))"
          :key="opt.testid"
          type="button"
          class="px-2 py-0.5 text-xs rounded hover:bg-neutral-100"
          :data-testid="opt.testid"
          @click="insertBlock('heading')"
        >
          {{ opt.label }}
        </button>
      </div>
      <div class="flex items-center gap-1 pr-2 border-r border-neutral-200">
        <button
          v-for="opt in INSERT_OPTIONS.filter((o) => !o.label.startsWith('H'))"
          :key="opt.testid"
          type="button"
          class="px-2 py-0.5 text-xs rounded hover:bg-neutral-100"
          :data-testid="opt.testid"
          @click="pickInsert(opt.id)()"
        >
          {{ opt.label }}
        </button>
      </div>
      <div class="flex items-center gap-1">
        <button
          type="button"
          class="px-2 py-0.5 text-xs rounded font-bold hover:bg-neutral-100"
          data-testid="md-marker-bold"
          :disabled="!markerTargetId()"
          @click="applyMarker('bold')"
        >
          B
        </button>
        <button
          type="button"
          class="px-2 py-0.5 text-xs rounded italic hover:bg-neutral-100"
          data-testid="md-marker-italic"
          :disabled="!markerTargetId()"
          @click="applyMarker('italic')"
        >
          I
        </button>
        <button
          type="button"
          class="px-2 py-0.5 text-xs rounded font-mono hover:bg-neutral-100"
          data-testid="md-marker-code"
          :disabled="!markerTargetId()"
          @click="applyMarker('code')"
        >
          {{ '</>' }}
        </button>
        <button
          type="button"
          class="px-2 py-0.5 text-xs rounded underline hover:bg-neutral-100"
          data-testid="md-marker-link"
          :disabled="!markerTargetId()"
          @click="applyMarker('link', 'https://')"
        >
          Link
        </button>
      </div>
    </div>

    <div
      v-if="isEmpty"
      class="text-xs text-neutral-500 italic px-1"
      data-testid="markdown-body-empty"
    >
      Empty body — use the toolbar above to add sections, lists, or tables.
    </div>

    <div class="flex flex-col gap-2">
      <div
        v-for="(block, index) in blocks"
        :key="block.id"
        class="group relative rounded border border-neutral-200 bg-white p-2"
        :class="{ 'ring-2 ring-blue-300': focusedBlockId === block.id }"
        :data-testid="`markdown-block-${block.kind}-${index}`"
        @focusin="setFocused(block.id)"
      >
        <div
          v-if="!readOnly"
          class="absolute top-1 right-1 flex items-center gap-0.5 opacity-0 group-hover:opacity-100 focus-within:opacity-100"
        >
          <button
            type="button"
            class="px-1 text-xs rounded hover:bg-neutral-100"
            :disabled="index === 0"
            :data-testid="`md-move-up-${index}`"
            @click="moveBlock(block.id, -1)"
          >
            ↑
          </button>
          <button
            type="button"
            class="px-1 text-xs rounded hover:bg-neutral-100"
            :disabled="index === blocks.length - 1"
            :data-testid="`md-move-down-${index}`"
            @click="moveBlock(block.id, 1)"
          >
            ↓
          </button>
          <button
            type="button"
            class="px-1 text-xs rounded text-red-600 hover:bg-red-50"
            :data-testid="`md-remove-${index}`"
            @click="removeBlock(block.id)"
          >
            ✕
          </button>
        </div>

        <template v-if="block.kind === 'heading'">
          <div class="flex items-center gap-2 pr-16">
            <select
              class="text-xs border border-neutral-300 rounded px-1 py-0.5"
              :value="blockDepth(block)"
              :disabled="readOnly"
              :data-testid="`md-heading-depth-${index}`"
              @change="patchBlock(block.id, { depth: Number(($event.target as HTMLSelectElement).value) as 1 | 2 | 3 } as Partial<Block>)"
            >
              <option :value="1">H1</option>
              <option :value="2">H2</option>
              <option :value="3">H3</option>
            </select>
            <input
              type="text"
              class="flex-1 text-sm font-semibold border border-neutral-300 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-blue-400"
              :value="block.text"
              :readonly="readOnly"
              :data-testid="`md-heading-text-${index}`"
              @input="patchBlock(block.id, { text: ($event.target as HTMLInputElement).value } as Partial<Block>)"
              @focus="setFocused(block.id)"
            >
          </div>
        </template>

        <template v-else-if="block.kind === 'paragraph'">
          <textarea
            rows="3"
            class="w-full text-sm border border-neutral-300 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-blue-400"
            :value="block.text"
            :readonly="readOnly"
            :data-testid="`md-paragraph-${index}`"
            @input="patchBlock(block.id, { text: ($event.target as HTMLTextAreaElement).value } as Partial<Block>)"
            @focus="setFocused(block.id)"
          />
        </template>

        <template v-else-if="block.kind === 'blockquote'">
          <textarea
            rows="3"
            class="w-full text-sm italic border-l-4 border-neutral-300 pl-2 focus:outline-none focus:ring-1 focus:ring-blue-400"
            :value="block.text"
            :readonly="readOnly"
            :data-testid="`md-quote-${index}`"
            @input="patchBlock(block.id, { text: ($event.target as HTMLTextAreaElement).value } as Partial<Block>)"
            @focus="setFocused(block.id)"
          />
        </template>

        <template v-else-if="block.kind === 'code'">
          <div class="flex items-center gap-2 pr-16">
            <input
              type="text"
              placeholder="lang"
              class="w-20 text-xs border border-neutral-300 rounded px-1 py-0.5 font-mono"
              :value="block.lang"
              :readonly="readOnly"
              :data-testid="`md-code-lang-${index}`"
              @input="patchBlock(block.id, { lang: ($event.target as HTMLInputElement).value } as Partial<Block>)"
            >
            <textarea
              rows="4"
              class="flex-1 text-xs font-mono border border-neutral-300 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-blue-400"
              :value="block.text"
              :readonly="readOnly"
              :data-testid="`md-code-text-${index}`"
              @input="patchBlock(block.id, { text: ($event.target as HTMLTextAreaElement).value } as Partial<Block>)"
            />
          </div>
        </template>

        <template v-else-if="block.kind === 'hr'">
          <div class="text-xs text-neutral-500 italic text-center" :data-testid="`md-hr-${index}`">
            — horizontal rule —
          </div>
        </template>

        <template v-else-if="block.kind === 'html'">
          <textarea
            rows="3"
            class="w-full text-xs font-mono border border-neutral-300 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-blue-400"
            :value="block.raw"
            :readonly="readOnly"
            :data-testid="`md-html-${index}`"
            @input="patchBlock(block.id, { raw: ($event.target as HTMLTextAreaElement).value } as Partial<Block>)"
            @focus="setFocused(block.id)"
          />
        </template>

        <template v-else-if="block.kind === 'list'">
          <div class="flex items-center gap-2 pr-16">
            <select
              class="text-xs border border-neutral-300 rounded px-1 py-0.5"
              :value="block.ordered ? 'ol' : 'ul'"
              :disabled="readOnly"
              :data-testid="`md-list-type-${index}`"
              @change="patchBlock(block.id, { ordered: ($event.target as HTMLSelectElement).value === 'ol' } as Partial<Block>)"
            >
              <option value="ul">• Unordered</option>
              <option value="ol">1. Ordered</option>
            </select>
            <textarea
              rows="3"
              :placeholder="block.ordered ? '1. item one\\n2. item two' : '- item one\\n- item two'"
              class="flex-1 text-sm border border-neutral-300 rounded px-2 py-1 focus:outline-none focus:ring-1 focus:ring-blue-400"
              :value="listText(block.items)"
              :readonly="readOnly"
              :data-testid="`md-list-items-${index}`"
              @input="setListItems(block.id, ($event.target as HTMLTextAreaElement).value)"
              @focus="setFocused(block.id)"
            />
            <div class="flex flex-col gap-1">
              <button
                type="button"
                class="px-1 text-xs rounded hover:bg-neutral-100"
                :data-testid="`md-list-add-${index}`"
                @click="addListItem(block.id)"
              >
                +
              </button>
            </div>
          </div>
          <ul
            v-if="!readOnly"
            class="mt-2 space-y-1 text-xs text-neutral-500 pr-16"
            :data-testid="`md-list-item-remove-${index}`"
          >
            <li v-for="(_, itemIdx) in block.items" :key="itemIdx" class="flex items-center gap-2">
              <span class="flex-1 truncate">{{ block.items[itemIdx] || '(empty)' }}</span>
              <button
                type="button"
                class="px-1 text-red-600 hover:bg-red-50 rounded"
                :disabled="block.items.length <= 1"
                :data-testid="`md-list-remove-${index}-${itemIdx}`"
                @click="removeListItem(block.id, itemIdx)"
              >
                ✕
              </button>
            </li>
          </ul>
        </template>

        <template v-else-if="block.kind === 'table'">
          <div class="pr-16 space-y-2">
            <div class="flex items-center gap-1">
              <button
                type="button"
                class="px-2 py-0.5 text-xs rounded hover:bg-neutral-100"
                :data-testid="`md-table-add-row-${index}`"
                @click="addTableRow(block.id)"
              >
                + Row
              </button>
              <button
                type="button"
                class="px-2 py-0.5 text-xs rounded hover:bg-neutral-100"
                :data-testid="`md-table-add-col-${index}`"
                @click="addTableColumn(block.id)"
              >
                + Col
              </button>
            </div>
            <table class="w-full text-xs border-collapse" :data-testid="`md-table-${index}`">
              <thead>
                <tr>
                  <th
                    v-for="(cell, colIdx) in block.header"
                    :key="`h-${colIdx}`"
                    class="border border-neutral-300 p-1"
                  >
                    <div class="flex items-center gap-1">
                      <input
                        type="text"
                        class="w-full text-xs font-semibold border border-neutral-200 rounded px-1 py-0.5"
                        :value="cell"
                        :readonly="readOnly"
                        :data-testid="`md-table-header-${index}-${colIdx}`"
                        @input="setTableHeader(block.id, colIdx, ($event.target as HTMLInputElement).value)"
                      >
                      <button
                        type="button"
                        class="px-1 text-red-600 hover:bg-red-50 rounded"
                        :disabled="block.header.length <= 1"
                        :data-testid="`md-table-remove-col-${index}-${colIdx}`"
                        @click="removeTableColumn(block.id, colIdx)"
                      >
                        ✕
                      </button>
                    </div>
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, rowIdx) in block.rows" :key="`r-${rowIdx}`">
                  <td
                    v-for="(_, colIdx) in block.header"
                    :key="`c-${rowIdx}-${colIdx}`"
                    class="border border-neutral-300 p-1"
                  >
                    <input
                      type="text"
                      class="w-full text-xs border border-neutral-200 rounded px-1 py-0.5"
                      :value="row[colIdx] ?? ''"
                      :readonly="readOnly"
                      :data-testid="`md-table-cell-${index}-${rowIdx}-${colIdx}`"
                      @input="setTableCell(block.id, rowIdx, colIdx, ($event.target as HTMLInputElement).value)"
                    >
                  </td>
                  <td>
                    <button
                      type="button"
                      class="px-1 text-red-600 hover:bg-red-50 rounded"
                      :disabled="block.rows.length <= 1"
                      :data-testid="`md-table-remove-row-${index}-${rowIdx}`"
                      @click="removeTableRow(block.id, rowIdx)"
                    >
                      ✕
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
        </template>
      </div>
    </div>

    <button
      v-if="!readOnly"
      type="button"
      class="self-start text-xs text-neutral-500 hover:text-neutral-700 px-2 py-1 rounded border border-dashed border-neutral-300"
      data-testid="md-append-block"
      @click="insertBlock('paragraph', blocks.length)"
    >
      + Add block
    </button>
  </div>
</template>
