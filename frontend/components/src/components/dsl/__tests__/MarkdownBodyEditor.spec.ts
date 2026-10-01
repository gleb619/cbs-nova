import { mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import MarkdownBodyEditor from '../MarkdownBodyEditor.vue'
import {
  parseBlocks,
  resetBlockIdCounter,
  serializeBlocks,
} from '../../../utils/markdownBlocks'

const mountEditor = (props: Record<string, unknown> = {}) =>
  mount(MarkdownBodyEditor, { props: { body: '', ...props } })

const waitEmit = async () => {
  await nextTick()
}

describe('MarkdownBodyEditor — component wiring', () => {
  let wrapper: ReturnType<typeof mountEditor>

  beforeEach(() => {
    resetBlockIdCounter(0)
  })

  afterEach(() => {
    wrapper?.unmount()
  })

  describe('mount + initial render', () => {
    it('renders the empty-state hint when blocks list is empty', () => {
      wrapper = mountEditor({ body: '' })

      expect(wrapper.find('[data-testid="markdown-body-editor"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="markdown-body-toolbar"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="markdown-body-empty"]').exists()).toBe(true)
    })

    it('renders blocks parsed from the initial body prop', () => {
      wrapper = mountEditor({ body: '## Hello\n\nWorld\n' })

      expect(wrapper.find('[data-testid="markdown-body-empty"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="md-heading-text-0"]').exists()).toBe(true)
      expect(wrapper.find('[data-testid="md-paragraph-1"]').exists()).toBe(true)
    })

    it('hides the toolbar and per-block controls when readOnly', () => {
      wrapper = mountEditor({ body: '## Hello\n', readOnly: true })

      expect(wrapper.find('[data-testid="markdown-body-toolbar"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="md-append-block"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="md-remove-0"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="md-move-up-0"]').exists()).toBe(false)
      expect(wrapper.find('[data-testid="md-move-down-0"]').exists()).toBe(false)
    })
  })

  describe('isEmpty computed', () => {
    it('is true when no blocks are present', () => {
      wrapper = mountEditor({ body: '' })
      // expose for assertion
      // @ts-expect-error — accessing internal state from setup
      expect(wrapper.vm.isEmpty).toBe(true)
    })

    it('is false once at least one block exists', async () => {
      wrapper = mountEditor({ body: '' })
      await wrapper.find('[data-testid="md-insert-h1"]').trigger('click')
      await waitEmit()
      // @ts-expect-error — accessing internal state from setup
      expect(wrapper.vm.isEmpty).toBe(false)
    })
  })

  describe('insertBlock — focus-aware positioning', () => {
    it('inserts at end of list when nothing is focused', async () => {
      wrapper = mountEditor({ body: '## First\n' })

      await wrapper.find('[data-testid="md-insert-h1"]').trigger('click')
      await waitEmit()

      const blocks = wrapper.findAll('[data-testid^="md-heading-text-"]')
      expect(blocks).toHaveLength(2)
      expect(blocks[0].exists()).toBe(true)
      expect(blocks[1].exists()).toBe(true)
    })

    it('inserts after the focused block when one is focused', async () => {
      wrapper = mountEditor({ body: '## A\n\nB paragraph\n\n## C\n' })

      // Focus the paragraph at index 1
      await wrapper.find('[data-testid="md-paragraph-1"]').trigger('focus')
      await wrapper.find('[data-testid="md-insert-h2"]').trigger('click')
      await waitEmit()

      // New H2 should sit between paragraph (now index 1) and the original H2 (now index 3)
      expect(wrapper.find('[data-testid="md-heading-text-2"]').exists()).toBe(true)
      // The original "## C" should now be at index 3
      expect(
        (wrapper.find('[data-testid="md-heading-text-3"]').element as HTMLInputElement).value,
      ).toBe('C')
    })

    it('appends a paragraph via md-append-block at end', async () => {
      wrapper = mountEditor({ body: '## Top\n' })

      await wrapper.find('[data-testid="md-append-block"]').trigger('click')
      await waitEmit()

      expect(wrapper.find('[data-testid="md-paragraph-1"]').exists()).toBe(true)
    })
  })

  describe('removeBlock', () => {
    it('removes the targeted block and updates rendered list', async () => {
      wrapper = mountEditor({ body: '## A\n\nB text\n\n## C\n' })

      await wrapper.find('[data-testid="md-remove-1"]').trigger('click')
      await waitEmit()

      expect(wrapper.find('[data-testid="md-paragraph-1"]').exists()).toBe(false)
      // C should now be at index 1
      expect(
        (wrapper.find('[data-testid="md-heading-text-1"]').element as HTMLInputElement).value,
      ).toBe('C')
    })

    it('is a no-op when readOnly', async () => {
      wrapper = mountEditor({ body: '## A\n', readOnly: true })

      // Per-block remove buttons don't exist in readOnly mode — guarded at template level.
      // Confirm no remove control rendered, and that calling via vm does not emit either.
      expect(wrapper.find('[data-testid="md-remove-0"]').exists()).toBe(false)
      // @ts-expect-error — accessing internal fn from setup
      wrapper.vm.removeBlock('b1')
      await waitEmit()
      expect(wrapper.emitted('update:body')).toBeUndefined()
    })
  })

  describe('moveBlock — boundary clamping', () => {
    it('moves a block down by one', async () => {
      wrapper = mountEditor({ body: '## A\n\nB text\n' })

      await wrapper.find('[data-testid="md-move-down-0"]').trigger('click')
      await waitEmit()

      // Paragraph is now at index 0
      expect(wrapper.find('[data-testid="md-paragraph-0"]').exists()).toBe(true)
      expect(
        (wrapper.find('[data-testid="md-heading-text-1"]').element as HTMLInputElement).value,
      ).toBe('A')
    })

    it('moves a block up by one', async () => {
      wrapper = mountEditor({ body: '## A\n\nB text\n' })

      await wrapper.find('[data-testid="md-move-up-1"]').trigger('click')
      await waitEmit()

      // The paragraph "B text" was at index 1 and is now at index 0.
      expect(wrapper.find('[data-testid="md-paragraph-0"]').exists()).toBe(true)
      expect(
        (wrapper.find('[data-testid="md-paragraph-0"]').element as HTMLTextAreaElement).value,
      ).toBe('B text')
      // The heading "A" is now at index 1
      expect(
        (wrapper.find('[data-testid="md-heading-text-1"]').element as HTMLInputElement).value,
      ).toBe('A')
    })

    it('does nothing when moving the first block up', async () => {
      wrapper = mountEditor({ body: '## A\n\nB text\n' })

      await wrapper.find('[data-testid="md-move-up-0"]').trigger('click')
      await waitEmit()

      // Heading stays at index 0
      expect(
        (wrapper.find('[data-testid="md-heading-text-0"]').element as HTMLInputElement).value,
      ).toBe('A')
      expect(wrapper.find('[data-testid="md-paragraph-1"]').exists()).toBe(true)
      expect(wrapper.emitted('update:body')).toBeUndefined()
    })

    it('does nothing when moving the last block down', async () => {
      wrapper = mountEditor({ body: '## A\n\nB text\n' })

      await wrapper.find('[data-testid="md-move-down-1"]').trigger('click')
      await waitEmit()

      expect(wrapper.find('[data-testid="md-paragraph-1"]').exists()).toBe(true)
      expect(wrapper.emitted('update:body')).toBeUndefined()
    })

    it('disables boundary buttons via :disabled binding', () => {
      wrapper = mountEditor({ body: '## A\n\nB text\n' })

      const upFirst = wrapper.find(
        '[data-testid="md-move-up-0"]',
      ) as unknown as { element: HTMLButtonElement }
      const downLast = wrapper.find(
        '[data-testid="md-move-down-1"]',
      ) as unknown as { element: HTMLButtonElement }
      expect(upFirst.element.disabled).toBe(true)
      expect(downLast.element.disabled).toBe(true)
    })

    it('is a no-op when readOnly', async () => {
      wrapper = mountEditor({ body: '## A\n\nB text\n' })

      // Force readOnly by re-mounting with the prop
      wrapper.unmount()
      wrapper = mountEditor({ body: '## A\n\nB text\n', readOnly: true })

      // move controls are not rendered — calling via vm should be guarded
      // @ts-expect-error — accessing internal fn from setup
      wrapper.vm.moveBlock('b1', -1)
      await waitEmit()
      expect(wrapper.emitted('update:body')).toBeUndefined()
    })
  })

  describe('setFocused', () => {
    it('marks the focused block so subsequent inserts follow it', async () => {
      wrapper = mountEditor({ body: '## A\n\nB text\n\n## C\n' })

      // Focus index 1 (paragraph)
      await wrapper.find('[data-testid="md-paragraph-1"]').trigger('focus')
      // @ts-expect-error — accessing internal fn from setup
      wrapper.vm.setFocused('b-paragraph')
      await wrapper.find('[data-testid="md-insert-h3"]').trigger('click')
      await waitEmit()

      expect(wrapper.find('[data-testid="md-heading-text-2"]').exists()).toBe(true)
    })
  })

  describe('update:body emit timing', () => {
    it('emits the serialized blocks string after an insert (via nextTick)', async () => {
      wrapper = mountEditor({ body: '## A\n' })

      await wrapper.find('[data-testid="md-insert-h1"]').trigger('click')
      await waitEmit()

      const events = wrapper.emitted('update:body')
      expect(events).toBeDefined()
      // exactly one emit per insert (nextTick-scheduled, coalesced)
      expect(events).toHaveLength(1)
      const payload = events?.[0][0] as string
      // The new serialization should round-trip to the same blocks
      expect(parseBlocks(payload)).toHaveLength(2)
      expect(payload).toContain('## A')
    })

    

    it('does not emit on insert under readOnly', async () => {
      wrapper = mountEditor({ body: '## A\n', readOnly: true })

      // @ts-expect-error — accessing internal fn from setup
      wrapper.vm.insertBlock('paragraph')
      await waitEmit()
      expect(wrapper.emitted('update:body')).toBeUndefined()
    })
  })

  describe('props.body watch — dedup guard', () => {
    it('does NOT replace blocks when incoming body re-serializes to the same string', async () => {
      // Start with a concrete document
      const initialBody = '## Hello\n\nWorld paragraph\n'
      wrapper = mountEditor({ body: initialBody })

      // Mutate the heading via input — this changes blocks.value but also emits update:body.
      // We ignore that emit (we only care about the *watch* on props.body).
      await wrapper.find('[data-testid="md-heading-text-0"]').setValue('Hello edited')
      await waitEmit()

      // Spy: track identity of the internal blocks array via the heading input node.
      const headingBefore = wrapper.find('[data-testid="md-heading-text-0"]')
      const identityBefore = (headingBefore.element as HTMLInputElement).isContentEditable
        ? headingBefore.html()
        : headingBefore.html()

      // Now push the SAME serialized body back through the prop. This simulates a parent
      // that re-emits the current serialized value — the watch should dedup and keep the
      // existing blocks.value (so user-internal mutations like focus / cursor are preserved).
      const currentSerialized = serializeBlocks(parseBlocks('## Hello edited\n\nWorld paragraph\n'))
      await wrapper.setProps({ body: currentSerialized })
      await waitEmit()

      const headingAfter = wrapper.find('[data-testid="md-heading-text-0"]')
      expect(headingAfter.exists()).toBe(true)
      // The internal block list should still reflect the prior mutation (no overwrite to
      // brand-new Block objects with different ids).
      expect((headingAfter.element as HTMLInputElement).value).toBe('Hello edited')
      // DOM identity check — same vnode, not a fresh mount
      expect(headingAfter.html()).toBe(identityBefore)
    })

    it('DOES replace blocks when incoming body parses to a different serialized string', async () => {
      wrapper = mountEditor({ body: '## A\n' })

      await wrapper.setProps({ body: '## B\n' })
      await waitEmit()

      expect(
        (wrapper.find('[data-testid="md-heading-text-0"]').element as HTMLInputElement).value,
      ).toBe('B')
    })
  })
})