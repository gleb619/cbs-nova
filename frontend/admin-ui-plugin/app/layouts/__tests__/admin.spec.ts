import { resetSavedDraftsState, useSavedDrafts } from '@cbs/components'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, type Mock, vi } from 'vitest'
import { defineComponent, h, nextTick, Suspense, type VNode } from 'vue'
import AdminLayout from '../admin.vue'

const { dslApi } = vi.hoisted(() => ({
  dslApi: { getDraftsMetadata: vi.fn(), listDrafts: vi.fn() },
}))

vi.mock('@cbs/admin-ui-plugin/composables/useDslApi', () => ({
  useDslApi: () => dslApi,
}))

const { navigateToMock } = vi.hoisted(() => ({ navigateToMock: vi.fn() }))

vi.mock('nuxt/app', async (importOriginal) => ({
  ...(await importOriginal<Record<string, unknown>>()),
  navigateTo: navigateToMock,
}))

const AppShell = defineComponent({
  props: ['navItems', 'linkComponent', 'title', 'shortTitle', 'activeClass', 'pad'],
  setup(_props, { slots }) {
    return () =>
      h('div', { 'data-testid': 'app-shell' }, [
        slots.toggle?.(),
        slots.brand?.(),
        slots.widgets?.(),
        slots.trailing?.(),
        slots.default?.(),
        slots.footer?.(),
      ])
  },
})

const AppSidebarToggle = defineComponent({
  setup() {
    return () => h('button', { 'data-testid': 'sidebar-toggle' }, 'Toggle')
  },
})

const AppFooter = defineComponent({
  props: ['copyright', 'buildInfo', 'gitInfo', 'docsBaseUrl'],
  setup() {
    return () => h('footer', { 'data-testid': 'app-footer' }, 'Footer')
  },
})

const NuxtLink = defineComponent({
  props: ['to'],
  setup(props) {
    return () => h('a', { href: props.to as string }, 'link')
  },
})

const sharedStubs = {
  AppShell,
  AppSidebarToggle,
  AppFooter,
  NuxtLink,
  teleport: true,
}

function mountAdminLayout(slots: Record<string, unknown> = {}) {
  const slotFns: Record<string, () => VNode> = {}
  for (const [key, component] of Object.entries(slots)) {
    slotFns[key] = () => h(component as ReturnType<typeof defineComponent>)
  }

  const Wrapper = defineComponent({
    setup() {
      return () => h(Suspense, null, { default: () => h(AdminLayout, null, slotFns) })
    },
  })
  return mount(Wrapper, {
    global: { stubs: sharedStubs },
    attachTo: document.body,
  })
}

const flush = async () => {
  await flushPromises()
  await nextTick()
  await nextTick()
}

describe('admin.vue auth affordance', () => {
  beforeEach(() => {
    resetSavedDraftsState()
    dslApi.listDrafts.mockReset()
    dslApi.listDrafts.mockResolvedValue([])
    navigateToMock.mockReset()
  })

  it('renders nothing when auth is disabled', async () => {
    vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
      public: { authEnabled: false },
    } as unknown as ReturnType<typeof useRuntimeConfig>)
    vi.mocked($fetch as unknown as Mock).mockResolvedValue({ authenticated: false })

    const wrapper = mountAdminLayout()
    await flush()

    expect(wrapper.find('[data-testid="auth-signin"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="auth-signout"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="auth-user"]').exists()).toBe(false)
  })

  it('renders Sign in when auth is enabled but not authenticated', async () => {
    vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
      public: { authEnabled: true },
    } as unknown as ReturnType<typeof useRuntimeConfig>)
    vi.mocked($fetch as unknown as Mock).mockResolvedValue({ authenticated: false })

    const wrapper = mountAdminLayout()
    await flush()

    expect(wrapper.find('[data-testid="auth-signin"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="auth-signout"]').exists()).toBe(false)
  })

  it('renders user + Sign out when authenticated', async () => {
    vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
      public: { authEnabled: true },
    } as unknown as ReturnType<typeof useRuntimeConfig>)
    vi.mocked($fetch as unknown as Mock).mockResolvedValue({
      authenticated: true,
      user: { sub: 'u-1', preferred_username: 'devuser' },
    })

    const wrapper = mountAdminLayout()
    await flush()

    expect(wrapper.find('[data-testid="auth-user"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="auth-user"]').text()).toBe('devuser')
    expect(wrapper.find('[data-testid="auth-signout"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="auth-signin"]').exists()).toBe(false)
  })
})

describe('admin.vue saved drafts widget', () => {
  beforeEach(() => {
    resetSavedDraftsState()
    dslApi.listDrafts.mockReset()
    dslApi.listDrafts.mockResolvedValue([])
    dslApi.getDraftsMetadata.mockReset()
    dslApi.getDraftsMetadata.mockResolvedValue({
      draftCount: 0,
      workbenchPath: '.workbench/drafts',
      sizeMb: 0,
      gitBranch: 'main',
      gitEnabled: true,
      statusCacheTtlSeconds: 5,
      historyLimit: 20,
    })
    navigateToMock.mockReset()
    vi.mocked(useRuntimeConfig as Mock).mockReturnValue({
      public: { authEnabled: false },
    } as unknown as ReturnType<typeof useRuntimeConfig>)
    vi.mocked($fetch as unknown as Mock).mockResolvedValue({ authenticated: false })
  })

  function mountLayout() {
    return mountAdminLayout()
  }

  it('mounts the widget in the navbar widgets slot and auto-loads drafts', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([
      { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
      { name: 'beta', type: 'Helper', status: 'Draft', updatedAt: 2 },
    ])

    const wrapper = mountLayout()
    await flush()

    expect(wrapper.find('[data-testid="dsl-saved-drafts-widget"]').exists()).toBe(true)
    expect(dslApi.listDrafts).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="dsl-saved-drafts-widget-count"]').text()).toBe('2')
  })

  it('opens the drawer with the draft list when Details is pressed', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([
      { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
    ])

    const wrapper = mountLayout()
    await flush()

    expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer"]').exists()).toBe(false)

    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')
    await flush()

    expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer"]').exists()).toBe(true)
    const items = wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')
    expect(items).toHaveLength(1)
    expect(items[0].text()).toContain('alpha')
  })

  it('loads and displays draft metadata when Details is opened', async () => {
    dslApi.getDraftsMetadata.mockResolvedValueOnce({
      draftCount: 7,
      workbenchPath: 'workspace/.workbench/drafts',
      sizeMb: 1.25,
      gitBranch: 'feature/drafts',
      gitEnabled: true,
      statusCacheTtlSeconds: 5,
      historyLimit: 20,
    })

    const wrapper = mountLayout()
    await flush()
    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')
    await flush()

    expect(dslApi.getDraftsMetadata).toHaveBeenCalledOnce()
    expect(wrapper.find('[data-testid="draft-metadata-card-count"]').text()).toBe('7')
    expect(wrapper.find('[data-testid="draft-metadata-card-path"]').text()).toBe(
      'workspace/.workbench/drafts',
    )
    expect(wrapper.find('[data-testid="draft-metadata-card-branch"]').text()).toBe('feature/drafts')
  })

  it('routes to the workbench with the draft in the query when nothing handles the pick', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([
      { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
    ])

    const wrapper = mountLayout()
    await flush()

    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')
    await flush()

    await wrapper.find('[data-testid="dsl-saved-drafts-item"]').trigger('click')
    await flush()

    expect(navigateToMock).toHaveBeenCalledWith({
      path: '/dsl-workbench',
      query: { draft: 'alpha' },
    })
  })

  it('lets a page registered in the layout slot take the pick instead of navigating', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([
      { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
    ])

    const onSelect = vi.fn()
    // Stands in for the workbench page: rendered through the layout's default
    // slot, so it shares the layout's Vue app and therefore its drafts store.
    const PageStub = defineComponent({
      setup() {
        useSavedDrafts({ onSelect })
        return () => h('div', { 'data-testid': 'page-stub' })
      },
    })

    const wrapper = mountAdminLayout({ default: PageStub })
    await flush()

    expect(wrapper.find('[data-testid="page-stub"]').exists()).toBe(true)

    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')
    await flush()
    await wrapper.find('[data-testid="dsl-saved-drafts-item"]').trigger('click')
    await flush()

    expect(onSelect).toHaveBeenCalledWith('alpha')
    expect(navigateToMock).not.toHaveBeenCalled()
  })

  it('surfaces a load failure on the badge and in the drawer', async () => {
    dslApi.listDrafts.mockRejectedValueOnce(new Error('drafts unavailable'))

    const wrapper = mountLayout()
    await flush()

    expect(wrapper.find('[data-testid="dsl-saved-drafts-widget-count"]').text()).toBe('0')

    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')
    await flush()

    expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer-error"]').text()).toContain(
      'drafts unavailable',
    )
  })
})
