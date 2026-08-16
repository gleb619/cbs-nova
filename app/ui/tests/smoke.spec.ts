import { describe, it, expect } from 'vitest'
import { mountSuspended } from '@nuxt/test-utils/runtime'
import IndexPage from '~/pages/index.vue'

describe('host portal smoke', () => {
  it('renders the landing page', async () => {
    const component = await mountSuspended(IndexPage)
    expect(component.text()).toContain('CBS Operator Portal')
    expect(component.text()).toContain('Open CBS Nova Admin')
  })
})
