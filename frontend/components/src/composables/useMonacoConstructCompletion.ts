import type * as Monaco from 'monaco-editor'
import type { ConstructType, DslConstruct } from '../types/dsl'

// Monaco's `CompletionItemKind` / `InsertTextRule` enum values (stable across
// versions). Same hardcoded-constant idiom as useMonacoHelperCompletion.
const METHOD_KIND = 0 as Monaco.languages.CompletionItemKind
const FUNCTION_KIND = 1 as Monaco.languages.CompletionItemKind
const CLASS_KIND = 5 as Monaco.languages.CompletionItemKind
const SNIPPET_KIND = 28 as Monaco.languages.CompletionItemKind
const INSERT_AS_SNIPPET = 4 as Monaco.languages.CompletionItemInsertTextRule

export interface BuildConstructCompletionItemsOptions {
  wordRange: Monaco.IRange
  constructs: DslConstruct[]
}

interface ConstructScaffold {
  label: string
  type: Exclude<ConstructType, 'Helper'>
  documentation: string
  insertText: string
}

// Scaffold snippets mirror the builder chains documented in
// docs/dsl/authoring.md (Builder API section).
const CONSTRUCT_SCAFFOLDS: ConstructScaffold[] = [
  {
    label: 'Dsl.process',
    type: 'Process',
    documentation:
      'Process scaffold — defines a Temporal Workflow that orchestrates transactions, helpers, and functions.',
    insertText: [
      'Dsl.process("${1:name}")',
      '    .taskQueue("${2:task-queue}")',
      '    .version("${3:1.0.0}")',
      '    .input(${4:InputType}.class)',
      '    .output(${5:OutputType}.class)',
      '    .execute(ctx -> {',
      '        $0',
      '    })',
      '    .build();',
    ].join('\n'),
  },
  {
    label: 'Dsl.transaction',
    type: 'Transaction',
    documentation:
      'Transaction scaffold — defines a durable, retryable Temporal Activity. Supports compensation for Saga rollbacks.',
    insertText: [
      'Dsl.transaction("${1:name}")',
      '    .taskQueue("${2:task-queue}")',
      '    .retryPolicy(r -> r.maxAttempts(${3:3}))',
      '    .input(${4:InputType}.class)',
      '    .output(${5:OutputType}.class)',
      '    .execute(ctx -> {',
      '        $0',
      '    })',
      '    .build();',
    ].join('\n'),
  },
  {
    label: 'Dsl.function',
    type: 'Function',
    documentation:
      'Function scaffold — lightweight local computation; no Temporal code is generated. May only call other functions and helpers.',
    insertText: [
      'Dsl.function("${1:name}")',
      '    .input(${2:InputType}.class)',
      '    .output(${3:OutputType}.class)',
      '    .execute(ctx -> {',
      '        $0',
      '    })',
      '    .build();',
    ].join('\n'),
  },
]

interface BuilderKeyword {
  label: string
  insertText: string
  documentation: string
  kind: Monaco.languages.CompletionItemKind
}

// Stage-builder / keyword vocabulary from the DSL authoring docs
// (docs/dsl/authoring.md). The constructs API exposes no stage-builder
// metadata, so these are a static vocabulary list, not derived fields.
const BUILDER_KEYWORDS: BuilderKeyword[] = [
  {
    label: '.taskQueue',
    insertText: '.taskQueue("${1:task-queue}")',
    documentation: 'Task queue the process or transaction runs on.',
    kind: METHOD_KIND,
  },
  {
    label: '.version',
    insertText: '.version("${1:1.0.0}")',
    documentation: 'Optional human-readable version label.',
    kind: METHOD_KIND,
  },
  {
    label: '.input',
    insertText: '.input(${1:InputType}.class)',
    documentation: 'Typed input record (@Json) for the construct.',
    kind: METHOD_KIND,
  },
  {
    label: '.output',
    insertText: '.output(${1:OutputType}.class)',
    documentation: 'Typed output record (@Json) for the construct.',
    kind: METHOD_KIND,
  },
  {
    label: '.parameters',
    insertText: '.parameters(reg -> {\n    reg.string("${1:name}")$0\n})',
    documentation: 'Parameter-based definition; receives/returns MapInput/MapOutput.',
    kind: METHOD_KIND,
  },
  {
    label: '.retryPolicy',
    insertText: '.retryPolicy(r -> r.maxAttempts(${1:3}))',
    documentation: 'Retry policy for a transaction.',
    kind: METHOD_KIND,
  },
  {
    label: '.startToCloseTimeout',
    insertText: '.startToCloseTimeout(Duration.ofSeconds(${1:30}))',
    documentation: 'Activity start-to-close timeout for a transaction.',
    kind: METHOD_KIND,
  },
  {
    label: '.compensation',
    insertText: ['.compensation(ctx -> {', '    $0', '})'].join('\n'),
    documentation:
      'Compensation block — runs in reverse order for Saga rollback. May only call helpers and functions.',
    kind: METHOD_KIND,
  },
  {
    label: '.execute',
    insertText: ['.execute(ctx -> {', '    $0', '})'].join('\n'),
    documentation: 'Execution logic of the construct.',
    kind: METHOD_KIND,
  },
  {
    label: '.build',
    insertText: '.build()',
    documentation: 'Builds the construct definition.',
    kind: METHOD_KIND,
  },
  {
    label: 'ctx.runHelper',
    insertText: 'ctx.runHelper("${1:name}", ${2:MapInput.of()})',
    documentation: 'Invokes a helper or function by name from DSL code.',
    kind: METHOD_KIND,
  },
  {
    label: 'ctx.complete',
    insertText: 'ctx.complete(${1:output})',
    documentation: 'Completes a process with its output payload.',
    kind: METHOD_KIND,
  },
  {
    label: 'Result.success',
    insertText: 'Result.success(${1:output})',
    documentation: 'Wraps a successful transaction/function result.',
    kind: METHOD_KIND,
  },
]

function constructItemKind(type: ConstructType): Monaco.languages.CompletionItemKind {
  switch (type) {
    case 'Function':
      return FUNCTION_KIND
    case 'Process':
    case 'Transaction':
      return CLASS_KIND
    default:
      return METHOD_KIND
  }
}

function isCompletableConstruct(type: ConstructType | undefined): type is
  | 'Process'
  | 'Transaction'
  | 'Function' {
  return type === 'Process' || type === 'Transaction' || type === 'Function'
}

export function buildConstructCompletionItems({
  wordRange,
  constructs,
}: BuildConstructCompletionItemsOptions): Monaco.languages.CompletionItem[] {
  const items: Monaco.languages.CompletionItem[] = CONSTRUCT_SCAFFOLDS.map((scaffold) => ({
    label: scaffold.label,
    kind: SNIPPET_KIND,
    detail: `${scaffold.type} scaffold`,
    documentation: scaffold.documentation,
    insertText: scaffold.insertText,
    insertTextRules: INSERT_AS_SNIPPET,
    range: wordRange,
  }))

  for (const construct of constructs) {
    // Helpers already surface through useMonacoHelperCompletion (same shared
    // registry) — skipping them here keeps the two providers disjoint.
    if (!isCompletableConstruct(construct.type)) continue
    items.push({
      label: construct.name,
      kind: constructItemKind(construct.type),
      detail:
        construct.inputType || construct.outputType
          ? `${construct.type} · ${construct.inputType ?? '?'} \u2192 ${construct.outputType ?? 'void'}`
          : construct.type,
      documentation: construct.description ?? '',
      insertText: construct.name,
      range: wordRange,
    })
  }

  for (const keyword of BUILDER_KEYWORDS) {
    items.push({
      label: keyword.label,
      kind: keyword.kind,
      detail: 'DSL stage builder',
      documentation: keyword.documentation,
      insertText: keyword.insertText,
      insertTextRules: INSERT_AS_SNIPPET,
      range: wordRange,
    })
  }

  return items
}

// '.' is the most useful trigger: typing `Dsl.` or chaining `.execute(` is the
// exact moment the author wants scaffold and stage-builder suggestions.
const TRIGGER_CHARACTERS = ['.', ' ']

interface UseMonacoConstructCompletionOptions {
  monaco: typeof Monaco
  getConstructs: () => Promise<DslConstruct[]>
  language?: string
}

interface RegisterState {
  refCount: number
  disposable: Monaco.IDisposable | null
}

const states = new Map<typeof Monaco, RegisterState>()

export function useMonacoConstructCompletion(
  options: UseMonacoConstructCompletionOptions,
): () => void {
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
        const constructs = await options.getConstructs()
        return { suggestions: buildConstructCompletionItems({ wordRange: range, constructs }) }
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
