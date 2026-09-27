import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { NotificationRule } from '../../types/notifications'
import NotificationRuleList from '../dsl/NotificationRuleList.vue'

function rule(overrides: Partial<NotificationRule> = {}): NotificationRule {
  return {
    id: 1,
    name: 'onboarding-failures',
    enabled: true,
    eventFilter: {
      eventType: 'RunFailed',
      definitionPattern: null,
      status: null,
    },
    actions: [{ sink: 'webhook', url: 'https://hooks.example.com/notify' }],
    priority: 0,
    rateClass: 'default',
    createdAt: '2025-01-01T00:00:00Z',
    updatedAt: '2025-01-01T00:00:00Z',
    ...overrides,
  }
}

function inputValue(wrapper: ReturnType<typeof mount>, testId: string): string {
  const input = wrapper.find(`[data-testid="${testId}"]`).element as HTMLInputElement
  return input.value
}

const mountList = (props: Record<string, unknown> = {}) =>
  mount(NotificationRuleList, { props: { rules: [], ...props } })

describe('NotificationRuleList', () => {
  it('exposes the root data-testid', () => {
    const wrapper = mountList()
    expect(wrapper.find('[data-testid="notification-rule-list"]').exists()).toBe(true)
  })

  it('renders one row per rule with name, event type, and condition details', () => {
    const wrapper = mountList({
      rules: [
        rule({
          id: 1,
          name: 'loan-failures',
          eventFilter: { eventType: 'RunFailed', definitionPattern: 'Loan*', status: 'FAILED' },
          actions: [{ sink: 'webhook', url: 'https://a.test' }],
          priority: 5,
          rateClass: 'urgent',
        }),
        rule({ id: 2, name: 'email-alerts', actions: [{ sink: 'email', to: 'ops@example.com' }] }),
      ],
    })

    const rows = wrapper.findAll('[data-testid="notification-rule-row"]')
    expect(rows).toHaveLength(2)

    const first = rows[0].text()
    expect(first).toContain('loan-failures')
    expect(first).toContain('RunFailed')
    expect(first).toContain('Loan*')
    expect(first).toContain('FAILED')
    expect(first).toContain('webhook')
    expect(first).toContain('priority 5')
    expect(first).toContain('urgent')

    const second = rows[1].text()
    expect(second).toContain('email-alerts')
    expect(second).toContain('email')
  })

  it('shows a disabled badge for disabled rules', () => {
    const wrapper = mountList({ rules: [rule({ id: 1, enabled: false })] })

    expect(wrapper.find('[data-testid="notification-rule-row"]').text()).toContain('disabled')
  })

  it('shows the empty state when no rules are provided', () => {
    const wrapper = mountList()

    expect(wrapper.find('[data-testid="notification-rule-list-empty"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('No notification rules configured.')
  })

  it('shows a loading skeleton while loading', () => {
    const wrapper = mountList({ loading: true })

    expect(wrapper.find('[data-testid="notification-rule-list-loading"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-testid="notification-rule-list-loading"] > div')).toHaveLength(3)
    expect(wrapper.find('[data-testid="notification-rule-list-empty"]').exists()).toBe(false)
  })

  it('shows the error state when an error is provided', () => {
    const wrapper = mountList({ error: 'Failed to load rules' })

    expect(wrapper.find('[data-testid="notification-rule-list-error"]').text()).toBe(
      'Failed to load rules',
    )
  })

  it('emits toggle with the rule id and inverted enabled state', async () => {
    const wrapper = mountList({
      rules: [rule({ id: 1, enabled: true }), rule({ id: 2, enabled: false })],
    })

    const toggles = wrapper.findAll('[data-testid="notification-rule-toggle"]')
    expect(toggles[0].text()).toBe('Enabled')
    expect(toggles[1].text()).toBe('Disabled')

    await toggles[0].trigger('click')
    await toggles[1].trigger('click')

    expect(wrapper.emitted('toggle')).toEqual([
      [1, false],
      [2, true],
    ])
  })

  it('disables the toggle button for the rule currently being edited', async () => {
    const wrapper = mountList({
      rules: [rule({ id: 1, name: 'editable' }), rule({ id: 2, name: 'other' })],
    })

    await wrapper.findAll('[data-testid="notification-rule-edit"]')[0].trigger('click')

    const toggles = wrapper.findAll('[data-testid="notification-rule-toggle"]')
    expect(toggles[0].attributes('disabled')).toBeDefined()
    expect(toggles[1].attributes('disabled')).toBeUndefined()
  })

  it('pre-fills the form when Edit is clicked and emits update on submit', async () => {
    const wrapper = mountList({
      rules: [
        rule({
          id: 7,
          name: 'edit-me',
          enabled: true,
          eventFilter: { eventType: 'RunCompleted', definitionPattern: 'Loan*', status: 'DONE' },
          actions: [{ sink: 'email', to: 'team@example.com' }],
          priority: 3,
          rateClass: 'batch',
        }),
      ],
    })

    await wrapper.find('[data-testid="notification-rule-edit"]').trigger('click')

    expect(wrapper.find('[data-testid="notification-rule-create-form"]').text()).toContain(
      'Edit rule #7',
    )
    expect(inputValue(wrapper, 'notification-rule-name-input')).toBe('edit-me')
    expect(inputValue(wrapper, 'notification-rule-definition-pattern-input')).toBe('Loan*')
    expect(inputValue(wrapper, 'notification-rule-status-input')).toBe('DONE')
    expect(inputValue(wrapper, 'notification-rule-to-input')).toBe('team@example.com')
    expect(inputValue(wrapper, 'notification-rule-priority-input')).toBe('3')
    expect(inputValue(wrapper, 'notification-rule-rate-class-input')).toBe('batch')
    expect(wrapper.find('[data-testid="notification-rule-submit"]').text()).toBe('Save Rule')

    await wrapper.find('[data-testid="notification-rule-create-form"]').trigger('submit')

    expect(wrapper.emitted('update')).toBeTruthy()
    expect(wrapper.emitted('update')?.[0]).toEqual([
      7,
      expect.objectContaining({
        name: 'edit-me',
        eventFilter: expect.objectContaining({
          eventType: 'RunCompleted',
          definitionPattern: 'Loan*',
          status: 'DONE',
        }),
        actions: [expect.objectContaining({ sink: 'email', to: 'team@example.com' })],
        priority: 3,
        rateClass: 'batch',
      }),
    ])
  })

  it('emits create with a payload when the create form is submitted', async () => {
    const wrapper = mountList()

    await wrapper.find('[data-testid="notification-rule-name-input"]').setValue('new-rule')
    await wrapper
      .find('[data-testid="notification-rule-event-type-input"]')
      .setValue('RunCompleted')
    await wrapper.find('[data-testid="notification-rule-url-input"]').setValue('https://new.test')

    await wrapper.find('[data-testid="notification-rule-create-form"]').trigger('submit')

    expect(wrapper.emitted('create')).toBeTruthy()
    expect(wrapper.emitted('create')?.[0]).toEqual([
      expect.objectContaining({
        name: 'new-rule',
        enabled: true,
        eventFilter: expect.objectContaining({ eventType: 'RunCompleted' }),
        actions: [expect.objectContaining({ sink: 'webhook', url: 'https://new.test' })],
      }),
    ])
  })

  it('shows a two-step delete confirmation and emits delete with the rule id', async () => {
    const wrapper = mountList({ rules: [rule({ id: 5, name: 'delete-me' })] })

    await wrapper.find('[data-testid="notification-rule-delete"]').trigger('click')

    expect(wrapper.find('[data-testid="notification-rule-delete-confirm-group"]').exists()).toBe(
      true,
    )

    await wrapper.find('[data-testid="notification-rule-delete-confirm"]').trigger('click')

    expect(wrapper.emitted('delete')).toEqual([[5]])
  })

  it('cancels delete confirmation without emitting delete', async () => {
    const wrapper = mountList({ rules: [rule({ id: 5, name: 'keep-me' })] })

    await wrapper.find('[data-testid="notification-rule-delete"]').trigger('click')
    await wrapper.find('[data-testid="notification-rule-delete-cancel"]').trigger('click')

    expect(wrapper.emitted('delete')).toBeFalsy()
    expect(wrapper.find('[data-testid="notification-rule-delete-confirm-group"]').exists()).toBe(
      false,
    )
  })
})
