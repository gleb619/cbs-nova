import { ref } from 'vue'

const collapsed = ref(false)
const mobileOpen = ref(false)

export function useSidebar() {
  function toggle() {
    collapsed.value = !collapsed.value
  }
  function collapse() {
    collapsed.value = true
  }
  function expand() {
    collapsed.value = false
  }
  function openMobile() {
    mobileOpen.value = true
  }
  function closeMobile() {
    mobileOpen.value = false
  }
  return { collapsed, mobileOpen, toggle, collapse, expand, openMobile, closeMobile }
}
