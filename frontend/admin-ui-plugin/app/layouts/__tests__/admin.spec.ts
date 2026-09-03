import { resetSavedDraftsState, useSavedDrafts } from '@cbs/components'
import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h } from 'vue'
import AdminLayout from '../admin.vue'

const { dslApi } = vi.hoisted(() => ({
  dslApi: { listDrafts: vi.fn() },
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

describe('admin.vue auth affordance', () => {
  beforeEach(() => {
    resetSavedDraftsState()
    dslApi.listDrafts.mockReset()
    dslApi.listDrafts.mockResolvedValue([])
    navigateToMock.mockReset()
  })

  it('renders nothing when auth is disabled', async () => {
    vi.mocked(useRuntimeConfig as never).mockReturnValue({
      public: { authEnabled: false },
    } as ReturnType<typeof useRuntimeConfig>)
    vi.mocked($fetch as never).mockResolvedValue({ authenticated: false })

    const wrapper = mount(AdminLayout, {
      global: {
        stubs: {
          AppShell,
          AppSidebarToggle,
          AppFooter,
          NuxtLink,
        },
      },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="auth-signin"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="auth-signout"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="auth-user"]').exists()).toBe(false)
  })

  it('renders Sign in when auth is enabled but not authenticated', async () => {
    vi.mocked(useRuntimeConfig as never).mockReturnValue({
      public: { authEnabled: true },
    } as ReturnType<typeof useRuntimeConfig>)
    vi.mocked($fetch as never).mockResolvedValue({ authenticated: false })

    const wrapper = mount(AdminLayout, {
      global: {
        stubs: {
          AppShell,
          AppSidebarToggle,
          AppFooter,
          NuxtLink,
        },
      },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="auth-signin"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="auth-signout"]').exists()).toBe(false)
  })

  it('renders user + Sign out when authenticated', async () => {
    vi.mocked(useRuntimeConfig as never).mockReturnValue({
      public: { authEnabled: true },
    } as ReturnType<typeof useRuntimeConfig>)
    vi.mocked($fetch as never).mockResolvedValue({
      authenticated: true,
      user: { sub: 'u-1', preferred_username: 'devuser' },
    })

    const wrapper = mount(AdminLayout, {
      global: {
        stubs: {
          AppShell,
          AppSidebarToggle,
          AppFooter,
          NuxtLink,
        },
      },
    })
    await flushPromises()

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
    navigateToMock.mockReset()
    vi.mocked(useRuntimeConfig as never).mockReturnValue({
      public: { authEnabled: false },
    } as ReturnType<typeof useRuntimeConfig>)
    vi.mocked($fetch as never).mockResolvedValue({ authenticated: false })
  })

  function mountLayout() {
    return mount(AdminLayout, {
      // The drawer teleports into <body>; stub Teleport so it renders in place.
      global: { stubs: { AppShell, AppSidebarToggle, AppFooter, NuxtLink, teleport: true } },
    })
  }

  it('mounts the widget in the navbar widgets slot and auto-loads drafts', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([
      { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
      { name: 'beta', type: 'Helper', status: 'Draft', updatedAt: 2 },
    ])

    const wrapper = mountLayout()
    await flushPromises()

    expect(wrapper.find('[data-testid="dsl-saved-drafts-widget"]').exists()).toBe(true)
    expect(dslApi.listDrafts).toHaveBeenCalled()
    expect(wrapper.find('[data-testid="dsl-saved-drafts-widget-count"]').text()).toBe('2')
  })

  it('opens the drawer with the draft list when Details is pressed', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([
      { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
    ])

    const wrapper = mountLayout()
    await flushPromises()

    expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer"]').exists()).toBe(false)

    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer"]').exists()).toBe(true)
    const items = wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')
    expect(items).toHaveLength(1)
    expect(items[0].text()).toContain('alpha')
  })

  it('routes to the workbench with the draft in the query when nothing handles the pick', async () => {
    dslApi.listDrafts.mockResolvedValueOnce([
      { name: 'alpha', type: 'Process', status: 'Draft', updatedAt: 1 },
    ])

    const wrapper = mountLayout()
    await flushPromises()
    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')
    await flushPromises()

    await wrapper.find('[data-testid="dsl-saved-drafts-item"]').trigger('click')
    await flushPromises()

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

    const wrapper = mount(AdminLayout, {
      global: { stubs: { AppShell, AppSidebarToggle, AppFooter, NuxtLink, teleport: true } },
      slots: { default: PageStub },
    })
    await flushPromises()

    expect(wrapper.find('[data-testid="page-stub"]').exists()).toBe(true)

    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-testid="dsl-saved-drafts-item"]').trigger('click')
    await flushPromises()

    expect(onSelect).toHaveBeenCalledWith('alpha')
    expect(navigateToMock).not.toHaveBeenCalled()
  })

  it('surfaces a load failure on the badge and in the drawer', async () => {
    dslApi.listDrafts.mockRejectedValueOnce(new Error('drafts unavailable'))

    const wrapper = mountLayout()
    await flushPromises()

    expect(wrapper.find('[data-testid="dsl-saved-drafts-widget-count"]').text()).toBe('0')

    await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer-error"]').text()).toContain(
      'drafts unavailable',
    )
  })
})
