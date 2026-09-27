import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { nextTick } from 'vue'
import WorkbenchHeader from '../WorkbenchHeader.vue'

describe('WorkbenchHeader', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
  })

  const props = {
    selectedConstruct: null,
    selectedConstructLabel: '',
    selectedPendingApproval: false,
    newShortcut: 'Ctrl+Alt+N',
    actionsShortcut: 'Ctrl+Alt+A',
    miscShortcut: 'Ctrl+Alt+M',
    actionItems: [
      { label: 'Share Link', value: 'share-link' },
      { label: 'Publish', value: 'publish', variant: 'primary' as const },
    ],
    helpersMenuItems: [
      { label: 'History', value: 'history' },
      { label: 'Diagnostics', value: 'diagnostics' },
    ],
  }

  function mountHeader(extraProps = {}) {
    return mount(WorkbenchHeader, {
      props: { ...props, ...extraProps },
      attachTo: document.body,
    })
  }

  it('renders header controls', () => {
    const wrapper = mountHeader()
    expect(wrapper.find('[data-testid="workbench-hotkey-new"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="workbench-hotkey-actions"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="workbench-hotkey-misc"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="workbench-new-definition"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('opens the Actions dropdown when its trigger is clicked', async () => {
    const wrapper = mountHeader()
    const wrapperActions = wrapper.find('[data-testid="workbench-hotkey-actions"]')
    const trigger = wrapperActions.find('[data-testid="dropdown-menu-trigger"]')
    expect(trigger.exists()).toBe(true)

    await trigger.trigger('click')
    await nextTick()

    const menu = wrapperActions.find('[data-testid="dropdown-menu-menu"]')
    expect(menu.exists()).toBe(true)
    expect(menu.find('[data-testid="dropdown-menu-item-share-link"]').exists()).toBe(true)
    expect(menu.find('[data-testid="dropdown-menu-item-publish"]').exists()).toBe(true)

    wrapper.unmount()
  })

  it('emits selectAction when an Actions menu item is clicked', async () => {
    const wrapper = mountHeader()
    const wrapperActions = wrapper.find('[data-testid="workbench-hotkey-actions"]')
    await wrapperActions.find('[data-testid="dropdown-menu-trigger"]').trigger('click')
    await nextTick()

    await wrapperActions.find('[data-testid="dropdown-menu-item-publish"]').trigger('click')

    expect(wrapper.emitted('selectAction')).toHaveLength(1)
    expect(wrapper.emitted('selectAction')?.[0]).toEqual([props.actionItems[1]])
    wrapper.unmount()
  })

  it('opens the Misc dropdown when its trigger is clicked', async () => {
    const wrapper = mountHeader()
    const wrapperMisc = wrapper.find('[data-testid="workbench-hotkey-misc"]')
    await wrapperMisc.find('[data-testid="dropdown-menu-trigger"]').trigger('click')
    await nextTick()

    const menu = wrapperMisc.find('[data-testid="dropdown-menu-menu"]')
    expect(menu.exists()).toBe(true)
    expect(menu.find('[data-testid="dropdown-menu-item-history"]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('emits toggleExplorer when the mobile hamburger is clicked', async () => {
    const wrapper = mountHeader()
    const button = wrapper.find('button[aria-label="Toggle explorer"]')
    expect(button.exists()).toBe(true)
    await button.trigger('click')
    expect(wrapper.emitted('toggleExplorer')).toHaveLength(1)
    wrapper.unmount()
  })

  it('emits newDefinition when the New button is clicked', async () => {
    const wrapper = mountHeader()
    await wrapper.find('[data-testid="workbench-new-definition"]').trigger('click')
    expect(wrapper.emitted('newDefinition')).toHaveLength(1)
    wrapper.unmount()
  })
})
