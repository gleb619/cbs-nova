import type { ChangeRequest } from '@cbs/components'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Ref } from 'vue'
import ApprovalsPage from '../approvals.vue'

// ---------------------------------------------------------------------------
// Harness for `useApprovals()` consumed by the approvals page, plus a
// controllable `useManifestGuard()` verdict.
// ---------------------------------------------------------------------------

interface ApprovalsHarness {
  items: Ref<ChangeRequest[]>
  loading: Ref<boolean>
  error: Ref<string | null>
  load: ReturnType<typeof vi.fn>
  submit: ReturnType<typeof vi.fn>
  approve: ReturnType<typeof vi.fn>
  reject: ReturnType<typeof vi.fn>
}

const { useApprovalsMock, useManifestGuardMock, guardState } = vi.hoisted(() => {
  const guardState = { allowed: true }
  const useApprovalsMockFn = vi.fn(() => {
    const harness = (globalThis as unknown as { __approvalsHarness?: ApprovalsHarness })
      .__approvalsHarness
    if (!harness) throw new Error('approvals harness not installed yet')
    return harness
  })
  const useManifestGuardMockFn = vi.fn(() => ({
    allowed: (_pieceId: string) => guardState.allowed,
    ready: { value: true },
  }))
  return {
    useApprovalsMock: useApprovalsMockFn,
    useManifestGuardMock: useManifestGuardMockFn,
    guardState,
  }
})

const harness: ApprovalsHarness = (() => {
  const vue = require('vue') as typeof import('vue')
  return {
    items: vue.ref<ChangeRequest[]>([]),
    loading: vue.ref(false),
    error: vue.ref<string | null>(null),
    load: vi.fn(),
    submit: vi.fn(),
    approve: vi.fn(),
    reject: vi.fn(),
  }
})()

;(globalThis as unknown as { __approvalsHarness?: ApprovalsHarness }).__approvalsHarness = harness

vi.mock('@cbs/admin-ui-plugin/composables/useApprovals', () => ({
  useApprovals: useApprovalsMock,
}))

vi.mock('@cbs/admin-ui-plugin/composables/useManifestGuard', () => ({
  useManifestGuard: useManifestGuardMock,
}))

function changeRequest(overrides: Partial<ChangeRequest> = {}): ChangeRequest {
  return {
    id: 1,
    definitionName: 'LoanDsl',
    draftContent: '{}',
    requestedBy: 'user-1',
    requestedAt: '2026-09-19T08:00:00Z',
    status: 'PENDING',
    approvedBy: null,
    approvedAt: null,
    comment: null,
    ...overrides,
  }
}

function mountPage() {
  return mount(ApprovalsPage, { attachTo: document.body })
}

const flush = async () => {
  await flushPromises()
}

describe('approvals.vue page wiring', () => {
  beforeEach(() => {
    harness.items.value = []
    harness.loading.value = false
    harness.error.value = null
    harness.load.mockReset()
    harness.submit.mockReset()
    harness.approve.mockReset()
    harness.reject.mockReset()
    guardState.allowed = true
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('calls load exactly once on mount', async () => {
    const wrapper = mountPage()
    await flush()

    expect(harness.load).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })

  it('renders one row per change request with definition, requester and status', async () => {
    harness.items.value = [
      changeRequest({ id: 2, definitionName: 'OtherDsl', status: 'APPROVED', comment: 'ok' }),
      changeRequest({ id: 1, definitionName: 'LoanDsl', status: 'PENDING' }),
    ]

    const wrapper = mountPage()
    await flush()

    const rows = wrapper.findAll('[data-testid="approvals-row"]')
    expect(rows).toHaveLength(2)
    // PENDING sorts first.
    expect(rows[0].find('[data-testid="approvals-definition"]').text()).toBe('LoanDsl')
    expect(rows[0].find('[data-testid="approvals-status"]').text()).toBe('PENDING')
    expect(rows[1].find('[data-testid="approvals-definition"]').text()).toBe('OtherDsl')
    expect(rows[1].text()).toContain('user-1')

    wrapper.unmount()
  })

  it('shows the empty state when there are no change requests', async () => {
    const wrapper = mountPage()
    await flush()

    expect(wrapper.find('[data-testid="approvals-empty"]').exists()).toBe(true)

    wrapper.unmount()
  })

  it('filters rows by the status select', async () => {
    harness.items.value = [
      changeRequest({ id: 1, status: 'PENDING' }),
      changeRequest({ id: 2, definitionName: 'OtherDsl', status: 'APPROVED', comment: 'ok' }),
    ]

    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="approvals-status-filter"]').setValue('APPROVED')
    await flush()

    const rows = wrapper.findAll('[data-testid="approvals-row"]')
    expect(rows).toHaveLength(1)
    expect(rows[0].find('[data-testid="approvals-definition"]').text()).toBe('OtherDsl')

    wrapper.unmount()
  })

  it('forwards approve clicks to the approve spy with the request id', async () => {
    harness.items.value = [changeRequest({ id: 7 })]
    harness.approve.mockResolvedValue(undefined)

    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="approvals-approve"]').trigger('click')
    await flush()

    expect(harness.approve).toHaveBeenCalledTimes(1)
    expect(harness.approve).toHaveBeenCalledWith(7)

    wrapper.unmount()
  })

  it('reject is a two-step inline confirm that forwards the reason', async () => {
    harness.items.value = [changeRequest({ id: 9 })]
    harness.reject.mockResolvedValue(undefined)

    const wrapper = mountPage()
    await flush()

    // First click arms the inline reason editor; confirm stays disabled.
    await wrapper.find('[data-testid="approvals-reject"]').trigger('click')
    await flush()

    const confirm = wrapper.find('[data-testid="approvals-reject-confirm"]')
    expect(wrapper.find('[data-testid="approvals-reject-reason"]').exists()).toBe(true)
    expect((confirm.element as HTMLButtonElement).disabled).toBe(true)

    await wrapper.find('[data-testid="approvals-reject-reason"]').setValue('breaking change')
    await flush()
    expect(
      (wrapper.find('[data-testid="approvals-reject-confirm"]').element as HTMLButtonElement)
        .disabled,
    ).toBe(false)

    await wrapper.find('[data-testid="approvals-reject-confirm"]').trigger('click')
    await flush()

    expect(harness.reject).toHaveBeenCalledTimes(1)
    expect(harness.reject).toHaveBeenCalledWith(9, 'breaking change')

    wrapper.unmount()
  })

  it('cancel dismisses the inline reject editor without calling reject', async () => {
    harness.items.value = [changeRequest({ id: 9 })]

    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="approvals-reject"]').trigger('click')
    await flush()
    await wrapper.find('[data-testid="approvals-reject-cancel"]').trigger('click')
    await flush()

    expect(harness.reject).not.toHaveBeenCalled()
    expect(wrapper.find('[data-testid="approvals-reject-reason"]').exists()).toBe(false)

    wrapper.unmount()
  })

  it('hides decision buttons for resolved requests', async () => {
    harness.items.value = [
      changeRequest({ id: 3, status: 'REJECTED', comment: 'nope', approvedBy: null }),
    ]

    const wrapper = mountPage()
    await flush()

    expect(wrapper.find('[data-testid="approvals-approve"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="approvals-reject"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('nope')

    wrapper.unmount()
  })

  it('disables the decision buttons when the manifest guard denies the piece', async () => {
    guardState.allowed = false
    harness.items.value = [changeRequest({ id: 7 })]

    const wrapper = mountPage()
    await flush()

    expect(
      (wrapper.find('[data-testid="approvals-approve"]').element as HTMLButtonElement).disabled,
    ).toBe(true)
    expect(
      (wrapper.find('[data-testid="approvals-reject"]').element as HTMLButtonElement).disabled,
    ).toBe(true)

    wrapper.unmount()
  })
})
