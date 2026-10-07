import { afterEach, describe, expect, it, vi } from 'vitest'
import { __resetMermaidRenderForTests, useMermaidRender } from '../useMermaidRender'

const initialize = vi.fn()
const render = vi.fn().mockResolvedValue({ svg: '<svg></svg>' })

vi.mock('mermaid', () => ({
  default: {
    initialize,
    render,
  },
}))

describe('useMermaidRender', () => {
  afterEach(() => {
    __resetMermaidRenderForTests()
    initialize.mockClear()
    render.mockClear()
  })

  it('initializes mermaid with strict security level', async () => {
    const { render: renderFn } = useMermaidRender()
    await renderFn('graph TD; A-->B', 'id-1')
    expect(initialize).toHaveBeenCalledWith(expect.objectContaining({ securityLevel: 'strict' }))
  })
})
