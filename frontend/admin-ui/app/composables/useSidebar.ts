import { ref } from 'vue'

const collapsed = ref(false)
const mobileOpen = ref(false)

export function useSidebar() {
  function toggle() { collapsed.value = !collapsed.value }
  function openMobile() { mobileOpen.value = true }
  function closeMobile() { mobileOpen.value = false }
  return { collapsed, mobileOpen, toggle, openMobile, closeMobile }
}