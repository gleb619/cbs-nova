import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import AppFooter from '../AppFooter.vue'

describe('AppFooter', () => {
  it('renders copyright and current year by default', () => {
    const wrapper = mount(AppFooter)
    const currentYear = new Date().getFullYear()
    expect(wrapper.text()).toContain(`© ${currentYear} CBS Nova`)
  })

  it('renders custom copyright and year', () => {
    const wrapper = mount(AppFooter, {
      props: {
        copyright: 'Acme Corp',
        year: 1999,
      },
    })
    expect(wrapper.text()).toContain('© 1999 Acme Corp')
  })

  it('renders default documentation links', () => {
    const wrapper = mount(AppFooter)
    const links = wrapper.findAll('nav a')
    expect(links).toHaveLength(3)
    expect(links[0].attributes('href')).toBe('/docs/architecture.md')
    expect(links[1].attributes('href')).toBe('/docs/frontend/index.md')
    expect(links[2].attributes('href')).toBe('/docs/frontend/runner.md')
  })

  it('renders build and git info when provided', () => {
    const wrapper = mount(AppFooter, {
      props: {
        buildInfo: {
          name: 'starter',
          version: '0.0.1-SNAPSHOT',
          time: '2026-08-16T12:51:34.439Z',
        },
        gitInfo: {
          branch: 'main',
          commit: {
            id: '6a49453',
            'id.full': '6a4945306762be262e16035f6fd153c102fa7a67',
            time: '2026-08-16T17:46:28+0500',
            message: { short: 'feat(kanban): add T225' },
          },
          dirty: true,
        },
      },
    })

    expect(wrapper.text()).toContain('starter v0.0.1-SNAPSHOT')
    expect(wrapper.text()).toContain('main')
    expect(wrapper.text()).toContain('6a49453')
    expect(wrapper.text()).toContain('feat(kanban): add T225')
    expect(wrapper.text()).toContain('(dirty)')
  })

  it('uses docsBaseUrl to build documentation links', () => {
    const wrapper = mount(AppFooter, {
      props: {
        docsBaseUrl: 'https://github.com/cbs-nova/cbs-nova/blob/main/docs/',
      },
    })
    const links = wrapper.findAll('nav a')
    expect(links[0].attributes('href')).toBe(
      'https://github.com/cbs-nova/cbs-nova/blob/main/docs/architecture.md',
    )
  })
})
