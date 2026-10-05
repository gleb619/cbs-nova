import { mount } from '@vue/test-utils'
import { afterEach, describe, expect, it } from 'vitest'
import type { ApiKey } from '../../../types/api-keys'
import ApiKeyList from '../ApiKeyList.vue'

const keys: ApiKey[] = [
  {
    id: 1,
    label: 'CI',
    prefix: 'ak_ci...',
    createdAt: '2026-09-01T10:00:00Z',
    lastUsedAt: '2026-09-02T10:00:00Z',
    revokedAt: null,
  },
  {
    id: 2,
    label: 'Backup',
    prefix: 'ak_bk...',
    createdAt: '2026-08-01T10:00:00Z',
    lastUsedAt: null,
    revokedAt: '2026-09-03T10:00:00Z',
  },
]

function mountList(props: Record<string, unknown>) {
  return mount(ApiKeyList, { props: props as never })
}

describe('ApiKeyList', () => {
  let wrapper: ReturnType<typeof mountList>

  afterEach(() => {
    wrapper?.unmount()
  })

  it('exposes the root data-testid', () => {
    wrapper = mountList({ apiKeys: [] })
    expect(wrapper.find('[data-testid="api-key-list"]').exists()).toBe(true)
  })

  it('renders key rows with name, prefix, created and last used timestamps', () => {
    wrapper = mountList({ apiKeys: keys })

    const rows = wrapper.findAll('[data-testid="api-key-row"]')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('CI')
    expect(rows[0].text()).toContain('ak_ci...')
    expect(rows[0].text()).toContain('9/1/2026')
    expect(rows[0].text()).toContain('9/2/2026')
  })

  it('shows the revoked badge only for revoked keys', () => {
    wrapper = mountList({ apiKeys: keys })

    const badges = wrapper.findAll('[data-testid="api-key-revoked-badge"]')
    expect(badges).toHaveLength(1)
    expect(badges[0].text()).toContain('9/3/2026')
  })

  it('shows the created secret banner when provided', () => {
    wrapper = mountList({ apiKeys: [], createdSecret: 'ak_live_plaintext' })

    expect(wrapper.find('[data-testid="api-key-secret-banner"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="api-key-secret"]').text()).toBe('ak_live_plaintext')
  })

  it('emits dismissSecret when the secret banner is dismissed', async () => {
    wrapper = mountList({ apiKeys: [], createdSecret: 'ak_secret' })

    await wrapper.find('[data-testid="api-key-secret-dismiss"]').trigger('click')

    expect(wrapper.emitted('dismissSecret')).toHaveLength(1)
  })

  it('emits create with the trimmed name when the form is submitted', async () => {
    wrapper = mountList({ apiKeys: [] })
    const input = wrapper.find('[data-testid="api-key-name-input"]')
    await input.setValue('  deploy-key  ')
    await wrapper.find('[data-testid="api-key-create-form"]').trigger('submit')

    expect(wrapper.emitted('create')).toHaveLength(1)
    expect(wrapper.emitted('create')?.[0]).toEqual(['deploy-key'])
  })

  it('does not emit create when the name is empty', async () => {
    wrapper = mountList({ apiKeys: [] })

    await wrapper.find('[data-testid="api-key-create-form"]').trigger('submit')

    expect(wrapper.emitted('create')).toBeUndefined()
  })

  it('requires a second confirm click before emitting revoke', async () => {
    wrapper = mountList({ apiKeys: keys })

    const buttons = wrapper.findAll('[data-testid="api-key-revoke"]')
    await buttons[0].trigger('click')

    expect(wrapper.emitted('revoke')).toBeUndefined()

    const rows = wrapper.findAll('[data-testid="api-key-row"]')
    expect(rows[0].find('[data-testid="api-key-revoke-confirm"]').exists()).toBe(true)
    expect(rows[0].find('[data-testid="api-key-revoke-cancel"]').exists()).toBe(true)

    await wrapper.find('[data-testid="api-key-revoke-confirm"]').trigger('click')

    expect(wrapper.emitted('revoke')).toHaveLength(1)
    expect(wrapper.emitted('revoke')?.[0]).toEqual([1])
  })

  it('cancels the pending revoke without emitting when Cancel is clicked', async () => {
    wrapper = mountList({ apiKeys: keys })

    await wrapper.findAll('[data-testid="api-key-revoke"]')[0].trigger('click')
    await wrapper.find('[data-testid="api-key-revoke-cancel"]').trigger('click')

    expect(wrapper.emitted('revoke')).toBeUndefined()
    expect(wrapper.find('[data-testid="api-key-revoke-confirm"]').exists()).toBe(false)
  })

  it('cancels the pending revoke when Escape is pressed', async () => {
    wrapper = mountList({ apiKeys: keys })

    await wrapper.findAll('[data-testid="api-key-revoke"]')[0].trigger('click')
    await wrapper.find('[data-testid="api-key-revoke-confirm"]').trigger('keydown', {
      key: 'Escape',
    })

    expect(wrapper.emitted('revoke')).toBeUndefined()
    expect(wrapper.find('[data-testid="api-key-revoke-confirm"]').exists()).toBe(false)
  })
})
