<script setup lang="ts">
withDefaults(
  defineProps<{
    title: string
    ariaLabel?: string
    testId?: string
    closeLabel: string
    widthClass?: string
  }>(),
  {
    ariaLabel: '',
    testId: 'drawer',
    widthClass: 'w-80',
  },
)

const open = defineModel<boolean>('open', { default: false })

function closeDrawer() {
  open.value = false
}
</script>

<template>
  <Teleport to="body">
    <Transition name="fade">
      <div
        v-if="open"
        class="fixed inset-0 z-30 bg-gray-900/50"
        aria-hidden="true"
        @click="closeDrawer"
      />
    </Transition>

    <Transition name="drawer">
      <aside
        v-if="open"
        :data-testid="testId"
        class="fixed top-0 right-0 z-40 h-full bg-gray-900 text-gray-100 flex flex-col shadow-xl border-l border-gray-800"
        :class="widthClass"
        role="dialog"
        aria-modal="true"
        :aria-label="ariaLabel || title"
      >
        <div class="flex items-center justify-between p-3 border-b border-gray-800">
          <h3 class="text-sm font-semibold text-gray-100">{{ title }}</h3>
          <button
            type="button"
            class="p-1.5 rounded hover:bg-gray-800 text-gray-400 hover:text-gray-100"
            :aria-label="closeLabel"
            data-testid="drawer-close-button"
            @click="closeDrawer"
          >
            ✕
          </button>
        </div>
        <slot />
      </aside>
    </Transition>
  </Teleport>
</template>

<style scoped>
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s;
}
.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.drawer-enter-active,
.drawer-leave-active {
  transition: transform 0.2s;
}
.drawer-enter-from,
.drawer-leave-to {
  transform: translateX(100%);
}
</style>
