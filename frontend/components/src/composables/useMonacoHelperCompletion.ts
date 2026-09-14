import type * as Monaco from 'monaco-editor'
import type { HelperCatalogEntry } from '../types/dsl'

// Monaco's `CompletionItemKind.Method` enum value (stable across versions).
const METHOD_KIND = 1 as Monaco.languages.CompletionItemKind

export interface BuildHelperCompletionItemsOptions {
  wordRange: Monaco.IRange
  catalog: HelperCatalogEntry[]
}

export function buildHelperCompletionItems({
  wordRange,
  catalog,
}: BuildHelperCompletionItemsOptions): Monaco.languages.CompletionItem[] {
  return catalog.map((entry) => ({
    label: entry.name,
    kind: METHOD_KIND,
    detail:
      entry.inputType || entry.outputType
        ? `${entry.inputType ?? '?'} \u2192 ${entry.outputType ?? 'void'}`
        : (entry.previewBehavior ?? ''),
    documentation: entry.description ?? '',
    insertText: entry.name,
    range: wordRange,
  }))
}

// " is the most useful trigger character: typing `ctx.runHelper("` is the
// exact moment the author wants a list of helper names.
const TRIGGER_CHARACTERS = ['"']

interface UseMonacoHelperCompletionOptions {
  monaco: typeof Monaco
  getCatalog: () => Promise<HelperCatalogEntry[]>
  language?: string
}

interface RegisterState {
  refCount: number
  disposable: Monaco.IDisposable | null
}

const states = new Map<typeof Monaco, RegisterState>()

export function useMonacoHelperCompletion(options: UseMonacoHelperCompletionOptions): () => void {
  const language = options.language ?? 'java'
  let state: RegisterState | undefined = states.get(options.monaco)
  if (!state) {
    state = { refCount: 0, disposable: null }
    states.set(options.monaco, state)
  }
  if (!state) throw new Error('unreachable: state is defined after init')

  const ownedState: RegisterState = state!

  function register(): Monaco.IDisposable {
    if (ownedState.disposable) return ownedState.disposable
    ownedState.disposable = options.monaco.languages.registerCompletionItemProvider(language, {
      triggerCharacters: TRIGGER_CHARACTERS,
      async provideCompletionItems(model, position) {
        const word = model.getWordUntilPosition(position)
        const range = {
          startLineNumber: position.lineNumber,
          endLineNumber: position.lineNumber,
          startColumn: word.startColumn,
          endColumn: word.endColumn,
        }
        const catalog = await options.getCatalog()
        return { suggestions: buildHelperCompletionItems({ wordRange: range, catalog }) }
      },
    })
    return ownedState.disposable
  }

  ownedState.refCount += 1
  register()

  return () => {
    ownedState.refCount -= 1
    if (ownedState.refCount <= 0 && ownedState.disposable) {
      ownedState.disposable.dispose()
      ownedState.disposable = null
      states.delete(options.monaco)
    }
  }
}
