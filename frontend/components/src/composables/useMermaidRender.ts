import { readonly, ref } from 'vue'

let moduleCache: typeof import('mermaid')['default'] | null = null
let renderCache: ((definition: string, id: string) => Promise<string>) | null = null
const unavailable = ref(false)
let sequence = 0

function makeId(prefix: string): string {
  return `${prefix}-${++sequence}-${Math.random().toString(36).slice(2, 9)}`
}

export function __resetMermaidRenderForTests(): void {
  moduleCache = null
  renderCache = null
  unavailable.value = false
  sequence = 0
}

export function useMermaidRender() {
  async function ensureMermaid(): Promise<
    ((definition: string, id: string) => Promise<string>) | null
  > {
    if (unavailable.value) return null
    if (renderCache) return renderCache
    try {
      const mermaid = (await import('mermaid')).default
      moduleCache = mermaid
      mermaid.initialize({ startOnLoad: false, securityLevel: 'strict', theme: 'default' })
      renderCache = async (definition, id) => {
        // biome-ignore lint/style/noNonNullAssertion: module is loaded inside ensureMermaid
        const { svg } = await moduleCache!.render(id, definition)
        return svg
      }
      return renderCache
    } catch {
      unavailable.value = true
      return null
    }
  }

  async function render(definition: string, id?: string): Promise<string> {
    const renderFn = await ensureMermaid()
    if (!renderFn) {
      throw new Error('mermaid unavailable')
    }
    return renderFn(definition, id ?? makeId('mermaid'))
  }

  return {
    render,
    unavailable: readonly(unavailable),
  }
}
