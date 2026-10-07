import { flushPromises, mount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import DocTab from '../dsl/DocTab.vue'
import MetadataPanel from '../dsl/MetadataPanel.vue'
import ExplainMarkdownView from '../runner/ExplainMarkdownView.vue'
import MermaidDiagram from '../runner/MermaidDiagram.vue'

vi.mock('../dsl/MonacoEditor.vue', () => ({
  default: {
    name: 'MonacoEditorStub',
    props: {
      modelValue: { type: String, default: '' },
      language: { type: String, default: 'java' },
      readOnly: { type: Boolean, default: false },
      placeholder: { type: String, default: '' },
    },
    emits: ['update:modelValue'],
    template: `<textarea
      data-testid="doc-tab-textarea"
      :readonly="readOnly"
      :placeholder="placeholder"
      :value="modelValue"
      @input="$emit('update:modelValue', $event.target.value)"
    />`,
  },
}))

vi.mock('../../composables/useMermaidRender', () => ({
  useMermaidRender: () => ({
    render: vi
      .fn()
      .mockResolvedValue(
        '<svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)" class="mermaid">' +
          '<script>alert(1)</script>' +
          '<a xlink:href="javascript:alert(1)">X</a>' +
          '<rect width="10" height="10"/>' +
          '</svg>',
      ),
    unavailable: { value: false },
  }),
}))

const XSS_DOC = `---
name: XssDoc
description: xss doc
---

# Title

<img src=x onerror="alert(1)" onload="x()">

[link](javascript:alert(1))

<script>alert(1)</script>
`

const XSS_DESCRIPTION = '<p onclick="alert(1)">ok <a href="javascript:alert(1)">x</a></p>'

const XSS_MARKDOWN =
  '# Title\n\n<img src=x onerror="alert(1)" onload="x()">\n\n[link](javascript:alert(1))\n\n<script>alert(1)</script>'

function expectSafeHtml(html: string) {
  expect(html).not.toMatch(/<script\b/i)
  expect(html).not.toMatch(/\son[a-z]+\s*=/i)
  expect(html.toLowerCase()).not.toContain('javascript:')
}

describe('XSS sanitization', () => {
  beforeEach(() => {
    window.localStorage.clear()
  })

  it('DocTab sanitizes markdown body in view mode', async () => {
    const wrapper = mount(DocTab, { props: { doc: XSS_DOC, name: 'XssDoc' } })
    await wrapper.find('[data-testid="doc-tab-mode-view"]').trigger('click')

    const body = wrapper.find('[data-testid="doc-tab-view-body"]')
    expect(body.exists()).toBe(true)
    expect(body.html()).toContain('<h1>Title</h1>')
    expectSafeHtml(body.html())
  })

  it('MetadataPanel sanitizes description html', async () => {
    const wrapper = mount(MetadataPanel, {
      props: {
        construct: {
          name: 'X',
          type: 'Process',
          status: 'Draft',
          description: XSS_DESCRIPTION,
        },
      },
    })
    await wrapper.find('[data-testid="metadata-panel-toggle"]').trigger('click')

    const description = wrapper.find('[data-testid="metadata-field-description"]')
    expect(description.exists()).toBe(true)
    expect(description.html()).toContain('ok')
    expectSafeHtml(description.html())
  })

  it('ExplainMarkdownView sanitizes markdown html', () => {
    const wrapper = mount(ExplainMarkdownView, { props: { markdown: XSS_MARKDOWN } })

    const rendered = wrapper.find('[data-testid="explain-markdown-rendered"]')
    expect(rendered.exists()).toBe(true)
    expect(rendered.html()).toContain('<h1>Title</h1>')
    expectSafeHtml(rendered.html())
  })

  it('ExplainMarkdownView sanitizes rendered prop override', () => {
    const wrapper = mount(ExplainMarkdownView, {
      props: {
        markdown: '# ignored',
        rendered: '<p onload="alert(1)"><a href="javascript:alert(1)">x</a></p>',
      },
    })

    const rendered = wrapper.find('[data-testid="explain-markdown-rendered"]')
    expect(rendered.exists()).toBe(true)
    expect(rendered.html()).toContain('<p>')
    expectSafeHtml(rendered.html())
  })

  it('MermaidDiagram sanitizes rendered svg', async () => {
    const wrapper = mount(MermaidDiagram, { props: { definition: 'graph TD; A-->B' } })
    await flushPromises()

    const diagram = wrapper.find('[data-testid="mermaid-diagram"]')
    expect(diagram.exists()).toBe(true)
    expect(diagram.html()).toContain('<svg')
    expect(diagram.html()).toContain('class="mermaid"')
    expectSafeHtml(diagram.html())
  })
})
