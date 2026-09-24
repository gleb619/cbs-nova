import { onBeforeUnmount, onMounted, type Ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'

export interface UseWorkbenchBeforeUnloadOptions {
  /** True when the workbench has unsaved server-side changes. */
  isDirty: Ref<boolean>
}

/**
 * Hooks up the workbench's two "unsaved changes" exits: the in-page
 * `beforeunload` listener that warns the user when they try to refresh/close
 * the tab, and the route-leave guard that prompts before navigating away.
 *
 * Decision: extracted from `dsl-workbench.vue` so the page stays a thin
 * orchestrator. The guard message + prompt text are kept identical to the
 * previous inline implementation.
 */
export function useWorkbenchBeforeUnload(options: UseWorkbenchBeforeUnloadOptions): void {
  const { isDirty } = options

  function handleBeforeUnload(event: BeforeUnloadEvent) {
    if (!isDirty.value) return
    event.preventDefault()
    event.returnValue = ''
  }

  onBeforeRouteLeave(() => {
    if (isDirty.value && !window.confirm('You have unsaved changes. Leave anyway?')) {
      return false
    }
    return true
  })

  onMounted(() => {
    window.addEventListener('beforeunload', handleBeforeUnload)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('beforeunload', handleBeforeUnload)
  })
}
