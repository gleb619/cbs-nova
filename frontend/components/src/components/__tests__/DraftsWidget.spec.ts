import { mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it } from 'vitest'
import { h } from 'vue'
import type { SavedDraftSummary } from '../../composables/useSavedDrafts'
import type { DraftsMetadata } from '../../types/drafts'
import DraftsWidget from '../dsl/DraftsWidget.vue'

const DRAFTS: SavedDraftSummary[] = [
  { name: 'alpha', type: 'Process', status: 'MODIFIED', updatedAt: 1 },
  { name: 'beta', type: 'Helper', status: 'ADDED', updatedAt: 2 },
]

const METADATA: DraftsMetadata = {
  draftCount: 2,
  sourcePath: '/workspace/dsl',
  sizeMb: 0.01,
  gitBranch: 'main',
  gitEnabled: true,
  statusCacheTtlSeconds: 5,
  workspaceMode: 'SHARED',
}

const NO_GIT_METADATA: DraftsMetadata = { ...METADATA, gitEnabled: false }

// The panel is a positioned dropdown (no teleport), so it renders in place.
const mountWidget = (props: Record<string, unknown> = {}, slots: Record<string, unknown> = {}) =>
  mount(DraftsWidget, {
    props: { drafts: DRAFTS, ...props },
    slots: slots as never,
  })

const open = async (wrapper: ReturnType<typeof mountWidget>) => {
  await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')
}

describe('DraftsWidget', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  describe('header', () => {
    it('renders the panel with a source-control heading and the draft count', () => {
      const wrapper = mountWidget()
      expect(wrapper.find('[data-testid="dsl-saved-drafts-widget"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="dsl-saved-drafts-widget-count"]').text()).toBe('2')
      expect(wrapper.text()).toContain('Source Control')
    })

    it('honours a custom title and the deprecated drawerTitle', () => {
      expect(mountWidget({ title: 'Changes' }).text()).toContain('Changes')
      expect(mountWidget({ drawerTitle: 'Legacy' }).text()).toContain('Legacy')
    })

    it('shows an ellipsis while the first load is in flight', () => {
      const wrapper = mountWidget({ drafts: [], loading: true })
      expect(wrapper.find('[data-testid="dsl-saved-drafts-widget-count"]').text()).toBe('…')
    })

    it('keeps showing the last known count while refreshing', () => {
      const wrapper = mountWidget({ loading: true })
      expect(wrapper.find('[data-testid="dsl-saved-drafts-widget-count"]').text()).toBe('2')
    })

    it('marks the badge and exposes the message when loading failed', () => {
      const wrapper = mountWidget({ drafts: [], error: 'boom' })
      const badge = wrapper.find('[data-testid="dsl-saved-drafts-widget-count"]')
      expect(badge.classes()).toContain('bg-red-100')
      expect(badge.attributes('title')).toBe('boom')
    })

    it('renders Fetch, Pull and Rebase buttons with accessible labels and tooltips', () => {
      const wrapper = mountWidget()
      for (const [id, label] of [
        ['fetch', 'Fetch from remote'],
        ['pull', 'Pull from remote'],
        ['rebase', 'Rebase onto remote'],
      ]) {
        const button = wrapper.find(`[data-testid="dsl-saved-drafts-${id}"]`)
        expect(button.exists()).toBe(true)
        expect(button.attributes('aria-label')).toBe(label)
        expect(button.attributes('title')).toBe(label)
      }
    })

    it('emits fetch, pull and rebase when the remote buttons are pressed', async () => {
      const wrapper = mountWidget()
      await wrapper.find('[data-testid="dsl-saved-drafts-fetch"]').trigger('click')
      await wrapper.find('[data-testid="dsl-saved-drafts-pull"]').trigger('click')
      await wrapper.find('[data-testid="dsl-saved-drafts-rebase"]').trigger('click')
      expect(wrapper.emitted('fetch')).toHaveLength(1)
      expect(wrapper.emitted('pull')).toHaveLength(1)
      expect(wrapper.emitted('rebase')).toHaveLength(1)
    })

    it('disables the remote buttons while loading or a remote op is in flight', () => {
      const loading = mountWidget({ loading: true })
      for (const op of ['fetch', 'pull', 'rebase']) {
        expect(
          loading.find(`[data-testid="dsl-saved-drafts-${op}"]`).attributes('disabled'),
        ).toBeDefined()
      }

      const busy = mountWidget({ fetching: true })
      for (const op of ['fetch', 'pull', 'rebase']) {
        expect(
          busy.find(`[data-testid="dsl-saved-drafts-${op}"]`).attributes('disabled'),
        ).toBeDefined()
      }
    })

    it('shows a pending label for the operation that is running', () => {
      expect(
        mountWidget({ fetching: true }).find('[data-testid="dsl-saved-drafts-fetch"]').text(),
      ).toBe('Fetching…')
      expect(
        mountWidget({ pulling: true }).find('[data-testid="dsl-saved-drafts-pull"]').text(),
      ).toBe('Pulling…')
      expect(
        mountWidget({ rebasing: true }).find('[data-testid="dsl-saved-drafts-rebase"]').text(),
      ).toBe('Rebasing…')
    })

    it('disables the remote buttons when the workspace has no git', () => {
      const wrapper = mountWidget({ metadata: NO_GIT_METADATA })
      for (const op of ['fetch', 'pull', 'rebase']) {
        expect(
          wrapper.find(`[data-testid="dsl-saved-drafts-${op}"]`).attributes('disabled'),
        ).toBeDefined()
      }
    })

    it('emits refresh from the header refresh button and disables it while loading', async () => {
      const wrapper = mountWidget()
      await wrapper.find('[data-testid="dsl-saved-drafts-drawer-refresh"]').trigger('click')
      expect(wrapper.emitted('refresh')).toHaveLength(1)
      await wrapper.setProps({ loading: true })
      expect(
        wrapper.find('[data-testid="dsl-saved-drafts-drawer-refresh"]').attributes('disabled'),
      ).toBeDefined()
    })
  })

  describe('panel visibility', () => {
    it('keeps the panel closed until the heading is pressed', async () => {
      const wrapper = mountWidget()
      expect(wrapper.find('[data-testid="dsl-saved-drafts-panel"]').exists()).toBe(false)
      await open(wrapper)
      expect(wrapper.find('[data-testid="dsl-saved-drafts-panel"]').exists()).toBe(true)
      expect(wrapper.emitted('open')).toHaveLength(1)
    })

    it('collapses the panel again on a second press', async () => {
      const wrapper = mountWidget()
      await open(wrapper)
      await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')
      expect(wrapper.find('[data-testid="dsl-saved-drafts-panel"]').exists()).toBe(false)
    })

    it('persists the open state across mounts', async () => {
      const wrapper = mountWidget()
      await open(wrapper)
      expect(window.localStorage.getItem('cbs-nova:drafts-widget:expanded')).toBe('true')

      const remounted = mountWidget()
      expect(remounted.find('[data-testid="dsl-saved-drafts-panel"]').exists()).toBe(true)
    })

    it('exposes the expanded state to assistive tech', async () => {
      const wrapper = mountWidget()
      const toggle = () => wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]')
      expect(toggle().attributes('aria-expanded')).toBe('false')
      expect(toggle().attributes('aria-label')).toBe('Toggle source control panel')
      await open(wrapper)
      expect(toggle().attributes('aria-expanded')).toBe('true')
    })
  })

  describe('body', () => {
    it('lists the changed files with a git status badge', async () => {
      const wrapper = mountWidget()
      await open(wrapper)
      const items = wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')
      expect(items).toHaveLength(2)
      expect(items[0].text()).toContain('alpha')

      const badges = wrapper.findAll('[data-testid="dsl-saved-drafts-item-status"]')
      expect(badges.map((badge) => badge.text())).toEqual(['Modified', 'Added'])
    })

    it('renders a per-file revert button that discards just that file', async () => {
      const wrapper = mountWidget()
      await open(wrapper)
      const rowActions = wrapper.findAll('[data-testid="dsl-saved-drafts-item-discard"]')
      expect(rowActions).toHaveLength(2)
      expect(rowActions[1].attributes('aria-label')).toBe('Revert beta')
      await rowActions[1].trigger('click')
      expect(wrapper.emitted('discard')).toEqual([['beta']])
    })

    it('emits select when a file row is picked and keeps the panel open', async () => {
      const wrapper = mountWidget()
      await open(wrapper)
      await wrapper.find('[data-testid="dsl-saved-drafts-item"]').trigger('click')
      expect(wrapper.emitted('select')).toEqual([['alpha']])
      expect(wrapper.find('[data-testid="dsl-saved-drafts-panel"]').exists()).toBe(true)
    })

    it('highlights the selected file', async () => {
      const wrapper = mountWidget({ selectedName: 'beta' })
      await open(wrapper)
      const items = wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')
      expect(items[0].classes()).not.toContain('bg-gray-700')
      expect(items[1].classes()).toContain('bg-gray-700')
    })

    it('renders the error inside the panel', async () => {
      const wrapper = mountWidget({ error: 'drafts unavailable' })
      await open(wrapper)
      expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer-error"]').text()).toContain(
        'drafts unavailable',
      )
    })

    it('omits the error region when there is no error', async () => {
      const wrapper = mountWidget()
      await open(wrapper)
      expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer-error"]').exists()).toBe(false)
    })

    it('keeps DraftMetadataCard above the file list as compact workspace info', async () => {
      const wrapper = mountWidget({ metadata: METADATA })
      await open(wrapper)
      expect(wrapper.find('[data-testid="draft-metadata-card"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="draft-metadata-card-branch"]').text()).toBe('main')
    })

    it('renders DraftMetadataCard in loading state when metadataLoading is true', async () => {
      const wrapper = mountWidget({ metadata: null, metadataLoading: true })
      await open(wrapper)
      expect(wrapper.find('[data-testid="draft-metadata-card-loading"]').exists()).toBe(true)
    })

    it('renders DraftMetadataCard error when metadataError is set', async () => {
      const wrapper = mountWidget({ metadataError: 'metadata unavailable' })
      await open(wrapper)
      expect(wrapper.find('[data-testid="draft-metadata-card-error"]').text()).toContain(
        'metadata unavailable',
      )
    })

    it('exposes drafts and a refresh callback to the header and footer slots', async () => {
      const wrapper = mountWidget(
        {},
        {
          header: (slotProps: { drafts: SavedDraftSummary[]; refresh: () => void }) =>
            h('button', { 'data-testid': 'slot-header', onClick: slotProps.refresh }, [
              `header:${slotProps.drafts.length}`,
            ]),
          footer: (slotProps: { drafts: SavedDraftSummary[] }) =>
            h('div', { 'data-testid': 'slot-footer' }, [`footer:${slotProps.drafts.length}`]),
        },
      )
      await open(wrapper)
      expect(wrapper.find('[data-testid="slot-header"]').text()).toBe('header:2')
      expect(wrapper.find('[data-testid="slot-footer"]').text()).toBe('footer:2')
      await wrapper.find('[data-testid="slot-header"]').trigger('click')
      expect(wrapper.emitted('refresh')).toHaveLength(1)
    })
  })

  describe('footer commit box', () => {
    it('renders a commit message input and both actions', async () => {
      const wrapper = mountWidget()
      await open(wrapper)
      const input = wrapper.find('[data-testid="dsl-saved-drafts-commit-message"]')
      expect(input.exists()).toBe(true)
      expect(input.attributes('aria-label')).toBe('Commit message')
      expect(wrapper.find('[data-testid="dsl-saved-drafts-commit"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="dsl-saved-drafts-revert-all"]').exists()).toBe(true)
    })

    it('emits commit with the trimmed message and clears the input', async () => {
      const wrapper = mountWidget()
      await open(wrapper)
      await wrapper
        .find('[data-testid="dsl-saved-drafts-commit-message"]')
        .setValue('  update flow  ')
      await wrapper.find('[data-testid="dsl-saved-drafts-commit"]').trigger('click')
      expect(wrapper.emitted('commit')).toEqual([['update flow']])
      expect(
        (
          wrapper.find('[data-testid="dsl-saved-drafts-commit-message"]')
            .element as HTMLTextAreaElement
        ).value,
      ).toBe('')
    })

    it('keeps commit disabled without a message, without changes or while committing', async () => {
      const wrapper = mountWidget()
      await open(wrapper)
      const commit = () => wrapper.find('[data-testid="dsl-saved-drafts-commit"]')
      expect(commit().attributes('disabled')).toBeDefined()

      await wrapper.find('[data-testid="dsl-saved-drafts-commit-message"]').setValue('msg')
      expect(commit().attributes('disabled')).toBeUndefined()

      await wrapper.setProps({ committing: true })
      expect(commit().attributes('disabled')).toBeDefined()
      expect(commit().text()).toBe('Committing…')

      await wrapper.setProps({ drafts: [] })
      await wrapper.setProps({ committing: false })
      expect(commit().attributes('disabled')).toBeDefined()
    })

    it('emits a bare discard for discard all', async () => {
      const wrapper = mountWidget()
      await open(wrapper)
      await wrapper.find('[data-testid="dsl-saved-drafts-revert-all"]').trigger('click')
      expect(wrapper.emitted('discard')).toEqual([[]])
    })

    it('disables discard all when there is nothing to discard', async () => {
      const wrapper = mountWidget({ drafts: [] })
      await open(wrapper)
      expect(
        wrapper.find('[data-testid="dsl-saved-drafts-revert-all"]').attributes('disabled'),
      ).toBeDefined()
    })
  })

  describe('loading lifecycle', () => {
    it('emits refresh on mount when autoLoad is set', () => {
      const wrapper = mountWidget({ autoLoad: true })
      expect(wrapper.emitted('refresh')).toHaveLength(1)
    })

    it('does not emit refresh on mount by default', () => {
      const wrapper = mountWidget()
      expect(wrapper.emitted('refresh')).toBeUndefined()
    })
  })
})
