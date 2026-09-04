import { readonly, ref } from 'vue'

const visible = ref(false)
let installed = false

function install() {
  if (installed || typeof window === 'undefined') return
  installed = true
  window.addEventListener('keydown', (event) => {
    if (event.key === 'Alt' && !event.repeat) visible.value = true
  })
  window.addEventListener('keyup', (event) => {
    if (event.key === 'Alt') visible.value = false
  })
  window.addEventListener('blur', () => {
    visible.value = false
  })
}

export function useHotkeyOverlay() {
  install()
  return { visible: readonly(visible) }
}
