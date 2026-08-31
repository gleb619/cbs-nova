import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import AdminLayout from '../admin.vue'

const AppShell = defineComponent({
  props: ['navItems', 'linkComponent', 'title', 'shortTitle', 'activeClass', 'pad'],
  setup(_props, { slots }) {
    return () =>
      h('div', { 'data-testid': 'app-shell' }, [
        slots.toggle?.(),
        slots.brand?.(),
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
