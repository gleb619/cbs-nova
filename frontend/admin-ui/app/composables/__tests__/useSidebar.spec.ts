import { describe, expect, it } from 'vitest'
import { useSidebar } from '../useSidebar'

describe('useSidebar', () => {
  it('toggles collapsed state', () => {
    const { collapsed, toggle } = useSidebar()
    const initial = collapsed.value
    toggle()
    expect(collapsed.value).toBe(!initial)
  })

  it('opens and closes mobile drawer', () => {
    const { mobileOpen, openMobile, closeMobile } = useSidebar()
    openMobile()
    expect(mobileOpen.value).toBe(true)
    closeMobile()
    expect(mobileOpen.value).toBe(false)
  })
})
