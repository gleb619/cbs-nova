<script setup lang="ts">
import { computed, nextTick, ref } from 'vue'

export interface DropdownMenuItem {
  label: string
  value: string
  disabled?: boolean
  variant?: 'default' | 'primary'
}

const props = withDefaults(
  defineProps<{
    label: string
    items: DropdownMenuItem[]
    menuId?: string
    align?: 'left' | 'right'
  }>(),
  { align: 'right', menuId: undefined },
)

const emit = defineEmits<{
  (e: 'select', item: DropdownMenuItem): void
}>()

const isOpen = ref(false)
const focusedIndex = ref(0)
const triggerRef = ref<HTMLButtonElement | null>(null)
const menuRef = ref<HTMLDivElement | null>(null)
const itemRefs = ref<HTMLButtonElement[]>([])

const menuPositionClass = computed(() =>
  props.align === 'right' ? 'right-0' : 'left-0',
)

function setItemRef(el: Element | null, index: number) {
  if (el instanceof HTMLButtonElement) itemRefs.value[index] = el
}

async function toggleDropdown() {
  const willOpen = !isOpen.value
  isOpen.value = willOpen
  if (willOpen) {
    await nextTick()
    focusItem(focusedIndex.value)
  }
}

function openDropdown() {
  if (isOpen.value) return
  isOpen.value = true
  focusedIndex.value = 0
}

function closeDropdown(returnFocus = true) {
  if (!isOpen.value) return
  isOpen.value = false
  if (returnFocus) triggerRef.value?.focus()
}

async function openAndFocusFirst() {
  openDropdown()
  await nextTick()
  focusItem(focusedIndex.value)
}

function focusItem(index: number) {
  const el = itemRefs.value[index]
  if (el) el.focus()
}

function navigate(direction: 1 | -1) {
  if (!props.items.length) return
  const len = props.items.length
  let next = focusedIndex.value
  // skip disabled items
  for (let i = 0; i < len; i++) {
    next = (next + direction + len) % len
    if (!props.items[next]?.disabled) break
  }
  focusedIndex.value = next
  focusItem(next)
}

function selectItem(item: DropdownMenuItem) {
  if (item.disabled) return
  emit('select', item)
  closeDropdown()
}

function handleFocusOut(event: FocusEvent) {
  const root = menuRef.value
  if (!root) return
  const next = event.relatedTarget as Node | null
  if (next && root.contains(next)) return
  // focus left menu: keep open if moving to trigger; close otherwise
  if (next === triggerRef.value) return
  closeDropdown(false)
}

defineExpose({ close: () => closeDropdown(true) })
</script>

<template>
  <div class="relative inline-block">
    <button
      ref="triggerRef"
      type="button"
      class="px-3 py-1.5 text-sm rounded border border-gray-300 hover:bg-gray-100 inline-flex items-center gap-1"
      aria-haspopup="true"
      :aria-expanded="isOpen"
      :aria-controls="menuId"
      @click="toggleDropdown"
      @keydown.down.prevent="openAndFocusFirst"
      @keydown.escape.prevent="closeDropdown(true)"
    >
      {{ label }}
      <span aria-hidden="true">▾</span>
    </button>

    <div
      v-if="isOpen"
      :id="menuId"
      ref="menuRef"
      role="menu"
      class="absolute mt-1 min-w-[12rem] bg-white border border-gray-200 rounded shadow-md py-1 z-10 text-left"
      :class="menuPositionClass"
      tabindex="-1"
      @focusout="handleFocusOut"
      @keydown.up.prevent="navigate(-1)"
      @keydown.down.prevent="navigate(1)"
      @keydown.escape.prevent="closeDropdown(true)"
    >
      <button
        v-for="(item, index) in items"
        :key="item.value"
        :ref="(el) => setItemRef(el as Element | null, index)"
        type="button"
        role="menuitem"
        :disabled="item.disabled"
        class="block w-full text-left px-3 py-1.5 text-sm hover:bg-gray-100 disabled:opacity-50 disabled:hover:bg-transparent focus:bg-gray-100 focus:outline-none"
        :class="item.variant === 'primary' ? 'text-blue-700 hover:bg-blue-50 focus:bg-blue-50' : ''"
        @click="selectItem(item)"
        @keydown.enter.prevent="selectItem(item)"
        @keydown.space.prevent="selectItem(item)"
      >
        {{ item.label }}
      </button>
    </div>
  </div>
</template>
