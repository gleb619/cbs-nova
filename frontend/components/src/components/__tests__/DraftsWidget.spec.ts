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

// The drawer teleports into <body>; stub Teleport so it renders in place.
const mountWidget = (props: Record<string, unknown> = {}, slots: Record<string, unknown> = {}) =>
  mount(DraftsWidget, {
    props: { drafts: DRAFTS, ...props },
    slots: slots as never,
    global: { stubs: { teleport: true } },
  })

const openDrawer = async (wrapper: ReturnType<typeof mountWidget>) => {
  await wrapper.find('[data-testid="dsl-saved-drafts-widget-details"]').trigger('click')
}

describe('DraftsWidget', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  describe('navbar pill', () => {
    it('renders the pill with the draft count', () => {
      const wrapper = mountWidget()
      expect(wrapper.find('[data-testid="dsl-saved-drafts-widget"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="dsl-saved-drafts-widget-count"]').text()).toBe('2')
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
  })

  describe('drawer', () => {
    it('keeps the drawer closed until Details is pressed', async () => {
      const wrapper = mountWidget()
      expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer"]').exists()).toBe(false)
      await openDrawer(wrapper)
      expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer"]').exists()).toBe(true)
      expect(wrapper.emitted('open')).toHaveLength(1)
    })

    it('persists the open drawer across mounts', async () => {
      const wrapper = mountWidget()
      await openDrawer(wrapper)
      expect(window.localStorage.getItem('cbs-nova:drafts-widget:drawer-open')).toBe('true')

      const remounted = mountWidget()
      expect(remounted.find('[data-testid="dsl-saved-drafts-drawer"]').exists()).toBe(true)
    })

    it('emits select and closes the drawer when a file is picked', async () => {
      const wrapper = mountWidget()
      await openDrawer(wrapper)
      await wrapper.find('[data-testid="dsl-saved-drafts-item"]').trigger('click')
      expect(wrapper.emitted('select')).toEqual([['alpha']])
      expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer"]').exists()).toBe(false)
    })

    it('emits refresh on mount when autoLoad is set', () => {
      expect(mountWidget({ autoLoad: true }).emitted('refresh')).toHaveLength(1)
      expect(mountWidget().emitted('refresh')).toBeUndefined()
    })
  })

  describe('panel header', () => {
    it('shows the panel title and honours a custom one', async () => {
      const wrapper = mountWidget()
      await openDrawer(wrapper)
      const header = wrapper.find('[data-testid="dsl-saved-drafts-panel-header"]')
      expect(header.exists()).toBe(true)
      expect(header.text()).toContain('Source Control')

      const custom = mountWidget({ title: 'Changes' })
      await openDrawer(custom)
      expect(custom.find('[data-testid="dsl-saved-drafts-panel-header"]').text()).toContain(
        'Changes',
      )
    })

    it('renders Fetch, Pull and Rebase with accessible labels and tooltips', async () => {
      const wrapper = mountWidget()
      await openDrawer(wrapper)
      for (const [op, label] of [
        ['fetch', 'Fetch from remote'],
        ['pull', 'Pull from remote'],
        ['rebase', 'Rebase onto remote'],
      ]) {
        const button = wrapper.find(`[data-testid="dsl-saved-drafts-${op}"]`)
        expect(button.exists()).toBe(true)
        expect(button.attributes('aria-label')).toBe(label)
        expect(button.attributes('title')).toBe(label)
      }
    })

    it('emits fetch, pull and rebase', async () => {
      const wrapper = mountWidget()
      await openDrawer(wrapper)
      await wrapper.find('[data-testid="dsl-saved-drafts-fetch"]').trigger('click')
      await wrapper.find('[data-testid="dsl-saved-drafts-pull"]').trigger('click')
      await wrapper.find('[data-testid="dsl-saved-drafts-rebase"]').trigger('click')
      expect(wrapper.emitted('fetch')).toHaveLength(1)
      expect(wrapper.emitted('pull')).toHaveLength(1)
      expect(wrapper.emitted('rebase')).toHaveLength(1)
    })

    it('shows a pending label for the running operation', async () => {
      const wrapper = mountWidget({ fetching: true, pulling: true, rebasing: true })
      await openDrawer(wrapper)
      expect(wrapper.find('[data-testid="dsl-saved-drafts-fetch"]').text()).toBe('Fetching…')
      expect(wrapper.find('[data-testid="dsl-saved-drafts-pull"]').text()).toBe('Pulling…')
      expect(wrapper.find('[data-testid="dsl-saved-drafts-rebase"]').text()).toBe('Rebasing…')
    })

    it('disables the remote buttons while loading or a remote op runs', async () => {
      const loading = mountWidget({ loading: true })
      await openDrawer(loading)
      for (const op of ['fetch', 'pull', 'rebase']) {
        expect(
          loading.find(`[data-testid="dsl-saved-drafts-${op}"]`).attributes('disabled'),
        ).toBeDefined()
      }

      const busy = mountWidget({ pulling: true })
      await openDrawer(busy)
      for (const op of ['fetch', 'pull', 'rebase']) {
        expect(
          busy.find(`[data-testid="dsl-saved-drafts-${op}"]`).attributes('disabled'),
        ).toBeDefined()
      }
    })

    it('disables the remote buttons when the workspace has no git', async () => {
      const wrapper = mountWidget({ metadata: NO_GIT_METADATA })
      await openDrawer(wrapper)
      for (const op of ['fetch', 'pull', 'rebase']) {
        expect(
          wrapper.find(`[data-testid="dsl-saved-drafts-${op}"]`).attributes('disabled'),
        ).toBeDefined()
      }
    })

    it('emits refresh from the drawer action and disables it while loading', async () => {
      const wrapper = mountWidget()
      await openDrawer(wrapper)
      const button = wrapper.find('[data-testid="dsl-saved-drafts-drawer-refresh"]')
      expect(button.exists()).toBe(true)
      expect(button.find('svg').exists()).toBe(true)
      expect(button.text()).toBe('')
      await button.trigger('click')
      expect(wrapper.emitted('refresh')).toHaveLength(1)

      await wrapper.setProps({ loading: true })
      const busy = wrapper.find('[data-testid="dsl-saved-drafts-drawer-refresh"]')
      expect(busy.attributes('disabled')).toBeDefined()
      expect(busy.find('svg').classes()).toContain('animate-spin')
    })
  })

  describe('panel body', () => {
    it('lists the changed files with a git status badge', async () => {
      const wrapper = mountWidget()
      await openDrawer(wrapper)
      const items = wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')
      expect(items).toHaveLength(2)
      expect(items[0].text()).toContain('alpha')
      expect(
        wrapper.findAll('[data-testid="dsl-saved-drafts-item-status"]').map((b) => b.text()),
      ).toEqual(['Modified', 'Added'])
    })

    it('renders a per-file revert button that discards just that file', async () => {
      const wrapper = mountWidget()
      await openDrawer(wrapper)
      const actions = wrapper.findAll('[data-testid="dsl-saved-drafts-item-discard"]')
      expect(actions).toHaveLength(2)
      expect(actions[1].attributes('aria-label')).toBe('Revert beta')
      await actions[1].trigger('click')
      expect(wrapper.emitted('discard')).toEqual([['beta']])
    })

    it('highlights the selected file', async () => {
      const wrapper = mountWidget({ selectedName: 'beta' })
      await openDrawer(wrapper)
      const items = wrapper.findAll('[data-testid="dsl-saved-drafts-item"]')
      expect(items[0].classes()).not.toContain('bg-gray-700')
      expect(items[1].classes()).toContain('bg-gray-700')
    })

    it('renders the error inside the body', async () => {
      const wrapper = mountWidget({ error: 'drafts unavailable' })
      await openDrawer(wrapper)
      expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer-error"]').text()).toContain(
        'drafts unavailable',
      )
    })

    it('omits the error region when there is no error', async () => {
      const wrapper = mountWidget()
      await openDrawer(wrapper)
      expect(wrapper.find('[data-testid="dsl-saved-drafts-drawer-error"]').exists()).toBe(false)
    })

    it('keeps the compact workspace info above the file list', async () => {
      const wrapper = mountWidget({ metadata: METADATA })
      await openDrawer(wrapper)
      expect(wrapper.find('[data-testid="draft-metadata-card"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="draft-metadata-card-branch"]').text()).toBe('main')
    })

    it('renders the workspace info in loading state', async () => {
      const wrapper = mountWidget({ metadata: null, metadataLoading: true })
      await openDrawer(wrapper)
      expect(wrapper.find('[data-testid="draft-metadata-card-loading"]').exists()).toBe(true)
    })

    it('renders the workspace info error', async () => {
      const wrapper = mountWidget({ metadataError: 'metadata unavailable' })
      await openDrawer(wrapper)
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
      await openDrawer(wrapper)
      expect(wrapper.find('[data-testid="slot-header"]').text()).toBe('header:2')
      expect(wrapper.find('[data-testid="slot-footer"]').text()).toBe('footer:2')
      await wrapper.find('[data-testid="slot-header"]').trigger('click')
      expect(wrapper.emitted('refresh')).toHaveLength(1)
    })
  })

  describe('panel footer', () => {
    it('renders the commit message input and both actions', async () => {
      const wrapper = mountWidget()
      await openDrawer(wrapper)
      expect(wrapper.find('[data-testid="dsl-saved-drafts-commit-box"]').exists()).toBe(true)
      const input = wrapper.find('[data-testid="dsl-saved-drafts-commit-message"]')
      expect(input.attributes('aria-label')).toBe('Commit message')
      expect(wrapper.find('[data-testid="dsl-saved-drafts-commit"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="dsl-saved-drafts-revert-all"]').exists()).toBe(true)
    })

    it('emits commit with the trimmed message and clears the input', async () => {
      const wrapper = mountWidget()
      await openDrawer(wrapper)
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
      await openDrawer(wrapper)
      const commit = () => wrapper.find('[data-testid="dsl-saved-drafts-commit"]')
      expect(commit().attributes('disabled')).toBeDefined()

      await wrapper.find('[data-testid="dsl-saved-drafts-commit-message"]').setValue('msg')
      expect(commit().attributes('disabled')).toBeUndefined()

      await wrapper.setProps({ committing: true })
      expect(commit().attributes('disabled')).toBeDefined()
      expect(commit().text()).toBe('Committing…')

      await wrapper.setProps({ drafts: [], committing: false })
      expect(commit().attributes('disabled')).toBeDefined()
    })

    it('emits a bare discard for discard all', async () => {
      const wrapper = mountWidget()
      await openDrawer(wrapper)
      await wrapper.find('[data-testid="dsl-saved-drafts-revert-all"]').trigger('click')
      expect(wrapper.emitted('discard')).toEqual([[]])
    })

    it('disables discard all when there is nothing to discard', async () => {
      const wrapper = mountWidget({ drafts: [] })
      await openDrawer(wrapper)
      expect(
        wrapper.find('[data-testid="dsl-saved-drafts-revert-all"]').attributes('disabled'),
      ).toBeDefined()
    })
  })
})
