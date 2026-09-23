import type { PromoteResult, PromotionDefinition } from '@cbs/components'
import { flushPromises, mount } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { Ref } from 'vue'
import PromotePage from '../promote.vue'

interface PromotionHarness {
  environments: Ref<Array<{ name: string }>>
  environmentsLoading: Ref<boolean>
  environmentsError: Ref<string | null>
  definitions: Ref<PromotionDefinition[]>
  definitionsLoading: Ref<boolean>
  definitionsError: Ref<string | null>
  preview: Ref<PromoteResult | null>
  previewLoading: Ref<boolean>
  previewError: Ref<string | null>
  applying: Ref<boolean>
  applyError: Ref<string | null>
  applyResult: Ref<PromoteResult | null>
  loadEnvironments: ReturnType<typeof vi.fn>
  loadDefinitions: ReturnType<typeof vi.fn>
  runPreview: ReturnType<typeof vi.fn>
  apply: ReturnType<typeof vi.fn>
  reset: ReturnType<typeof vi.fn>
}

const { usePromotionMock } = vi.hoisted(() => {
  const usePromotionMockFn = vi.fn(() => {
    const harness = (globalThis as unknown as { __promotionHarness?: PromotionHarness })
      .__promotionHarness
    if (!harness) throw new Error('promotion harness not installed yet')
    return harness
  })
  return { usePromotionMock: usePromotionMockFn }
})

;(globalThis as unknown as { __promotionHarness?: PromotionHarness | null }).__promotionHarness =
  null

vi.mock('@cbs/admin-ui-plugin/composables/usePromotion', () => ({
  usePromotion: usePromotionMock,
}))

function installHarness(overrides: Partial<PromotionHarness> = {}): PromotionHarness {
  const vue = require('vue') as typeof import('vue')
  const harness: PromotionHarness = {
    environments: vue.ref([{ name: 'dev' }, { name: 'staging' }]),
    environmentsLoading: vue.ref(false),
    environmentsError: vue.ref<string | null>(null),
    definitions: vue.ref<PromotionDefinition[]>([]),
    definitionsLoading: vue.ref(false),
    definitionsError: vue.ref<string | null>(null),
    preview: vue.ref<PromoteResult | null>(null),
    previewLoading: vue.ref(false),
    previewError: vue.ref<string | null>(null),
    applying: vue.ref(false),
    applyError: vue.ref<string | null>(null),
    applyResult: vue.ref<PromoteResult | null>(null),
    loadEnvironments: vi.fn(),
    loadDefinitions: vi.fn(),
    runPreview: vi.fn(),
    apply: vi.fn(),
    reset: vi.fn(),
    ...overrides,
  }
  ;(globalThis as unknown as { __promotionHarness?: PromotionHarness }).__promotionHarness = harness
  return harness
}

function promoteResult(overrides: Partial<PromoteResult> = {}): PromoteResult {
  return {
    dryRun: true,
    reloaded: false,
    published: 2,
    failed: 0,
    results: [
      { name: 'LoanDsl', outcome: 'created' },
      { name: 'ReportDsl', outcome: 'updated', message: 'definition differs (+12 bytes)' },
      { name: 'AuditDsl', outcome: 'unchanged' },
    ],
    ...overrides,
  }
}

function mountPage() {
  return mount(PromotePage, { attachTo: document.body })
}

beforeEach(() => {
  installHarness()
})

afterEach(() => {
  document.body.innerHTML = ''
})

describe('promote page (T569)', () => {
  it('loads environments on mount and shows empty-config guidance when none exist', async () => {
    const harness = installHarness({
      environments: (() => {
        const vue = require('vue') as typeof import('vue')
        return vue.ref<Array<{ name: string }>>([])
      })(),
    })

    const wrapper = mountPage()
    await flushPromises()

    expect(harness.loadEnvironments).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="promote-no-environments"]').exists()).toBe(true)
  })

  it('loads definitions when a source environment is selected', async () => {
    const harness = installHarness()
    harness.definitions.value = [
      { name: 'LoanDsl', type: 'process', status: 'Published' },
      { name: 'ReportDsl', type: 'transaction', status: 'Published' },
    ]
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="promote-source-select"]').setValue('dev')
    await flushPromises()

    expect(harness.loadDefinitions).toHaveBeenCalledWith('dev')
    expect(wrapper.find('[data-testid="promote-def-LoanDsl"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="promote-def-ReportDsl"]').exists()).toBe(true)
  })

  it('previews the diff with CREATED/UPDATED/UNCHANGED outcomes before applying', async () => {
    const harness = installHarness()
    harness.definitions.value = [
      { name: 'LoanDsl', type: 'process', status: 'Published' },
      { name: 'ReportDsl', type: 'transaction', status: 'Published' },
    ]
    harness.runPreview.mockImplementation(() => {
      harness.preview.value = promoteResult()
      return Promise.resolve(harness.preview.value)
    })
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="promote-source-select"]').setValue('dev')
    await wrapper.find('[data-testid="promote-target-select"]').setValue('staging')
    await wrapper.find('[data-testid="promote-def-LoanDsl"] input').setValue(true)
    await wrapper.find('[data-testid="promote-preview-button"]').trigger('click')
    await flushPromises()

    expect(harness.runPreview).toHaveBeenCalledWith({
      source: 'dev',
      target: 'staging',
      definitions: ['LoanDsl'],
      includeDrafts: undefined,
    })
    expect(wrapper.find('[data-testid="promotion-diff-table"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="promotion-outcome-created"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="promotion-outcome-updated"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="promotion-outcome-unchanged"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="promote-apply-button"]').exists()).toBe(true)
  })

  it('apply promotes the bundle and shows the result with an audit link', async () => {
    const harness = installHarness()
    harness.definitions.value = [{ name: 'LoanDsl', type: 'process', status: 'Published' }]
    harness.preview.value = promoteResult({ dryRun: false })
    harness.apply.mockImplementation(() => {
      harness.applyResult.value = promoteResult({
        dryRun: false,
        results: [
          { name: 'LoanDsl', outcome: 'published' },
          { name: 'ReportDsl', outcome: 'published' },
        ],
      })
      return Promise.resolve(harness.applyResult.value)
    })
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="promote-source-select"]').setValue('dev')
    await wrapper.find('[data-testid="promote-target-select"]').setValue('staging')
    await wrapper.find('[data-testid="promote-preview-button"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="promote-apply-button"]').trigger('click')
    await flushPromises()

    expect(harness.apply).toHaveBeenCalledWith({
      source: 'dev',
      target: 'staging',
      definitions: undefined,
      includeDrafts: undefined,
    })
    expect(wrapper.find('[data-testid="promote-apply-result"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="promotion-outcome-published"]').exists()).toBe(true)
    const auditLink = wrapper.find('[data-testid="promote-audit-link"]')
    expect(auditLink.exists()).toBe(true)
    expect(auditLink.attributes('href')).toContain('PROMOTION')
  })

  it('surfaces preview and apply errors', async () => {
    const harness = installHarness()
    harness.definitions.value = [{ name: 'LoanDsl', type: 'process', status: 'Published' }]
    harness.runPreview.mockImplementation(() => {
      harness.previewError.value = 'backend unavailable'
      return Promise.resolve(null)
    })
    const wrapper = mountPage()
    await flushPromises()

    await wrapper.find('[data-testid="promote-source-select"]').setValue('dev')
    await wrapper.find('[data-testid="promote-target-select"]').setValue('staging')
    await wrapper.find('[data-testid="promote-preview-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="promote-preview-error"]').exists()).toBe(true)

    harness.previewError.value = null
    harness.runPreview.mockImplementation(() => {
      harness.preview.value = promoteResult()
      return Promise.resolve(harness.preview.value)
    })
    await wrapper.find('[data-testid="promote-preview-button"]').trigger('click')
    await flushPromises()

    harness.apply.mockImplementation(() => {
      harness.applyError.value = 'role OPERATOR required'
      return Promise.resolve(null)
    })
    await wrapper.find('[data-testid="promote-apply-button"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="promote-apply-error"]').exists()).toBe(true)
  })
})
