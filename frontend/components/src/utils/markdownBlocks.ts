import { marked, type Tokens } from 'marked'

export type BlockKind =
  | 'heading'
  | 'paragraph'
  | 'list'
  | 'table'
  | 'code'
  | 'hr'
  | 'blockquote'
  | 'html'

export interface HeadingBlock {
  id: string
  kind: 'heading'
  depth: 1 | 2 | 3
  text: string
}

export interface ParagraphBlock {
  id: string
  kind: 'paragraph'
  text: string
}

export interface ListBlock {
  id: string
  kind: 'list'
  ordered: boolean
  items: string[]
}

export interface TableBlock {
  id: string
  kind: 'table'
  header: string[]
  rows: string[][]
}

export interface CodeBlock {
  id: string
  kind: 'code'
  lang: string
  text: string
}

export interface HrBlock {
  id: string
  kind: 'hr'
}

export interface BlockquoteBlock {
  id: string
  kind: 'blockquote'
  text: string
}

export interface HtmlBlock {
  id: string
  kind: 'html'
  raw: string
}

export type Block =
  | HeadingBlock
  | ParagraphBlock
  | ListBlock
  | TableBlock
  | CodeBlock
  | HrBlock
  | BlockquoteBlock
  | HtmlBlock

let counter = 0
function nextId(): string {
  counter += 1
  return `b${counter}`
}

export function resetBlockIdCounter(value = 0): void {
  counter = value
}

export function createBlock(kind: BlockKind): Block {
  const id = nextId()
  switch (kind) {
    case 'heading':
      return { id, kind: 'heading', depth: 2, text: '' }
    case 'paragraph':
      return { id, kind: 'paragraph', text: '' }
    case 'list':
      return { id, kind: 'list', ordered: false, items: [''] }
    case 'table':
      return {
        id,
        kind: 'table',
        header: ['', ''],
        rows: [
          ['', ''],
          ['', ''],
        ],
      }
    case 'code':
      return { id, kind: 'code', lang: '', text: '' }
    case 'hr':
      return { id, kind: 'hr' }
    case 'blockquote':
      return { id, kind: 'blockquote', text: '' }
    case 'html':
      return { id, kind: 'html', raw: '' }
  }
}

function escapeTableCell(text: string): string {
  return text.replace(/\|/g, '\\|').replace(/\n/g, ' ')
}

function alignMarker(align: 'left' | 'center' | 'right' | null | undefined): string {
  if (align === 'center') return ':---:'
  if (align === 'right') return '---:'
  if (align === 'left') return ':---'
  return '---'
}

export function serializeBlocks(blocks: Block[]): string {
  const parts: string[] = []
  for (const block of blocks) {
    switch (block.kind) {
      case 'heading': {
        const hashes = '#'.repeat(block.depth)
        parts.push(block.text ? `${hashes} ${block.text}` : `${hashes} `)
        break
      }
      case 'paragraph':
        parts.push(block.text)
        break
      case 'list': {
        const marker = block.ordered ? '1.' : '-'
        const items = block.items.length > 0 ? block.items : ['']
        parts.push(items.map((item) => (item ? `${marker} ${item}` : `${marker} `)).join('\n'))
        break
      }
      case 'table': {
        const header = block.header.length > 0 ? block.header : ['']
        const rows = block.rows.length > 0 ? block.rows : [['']]
        const headerLine = `| ${header.map(escapeTableCell).join(' | ')} |`
        const separator = `| ${header.map(() => alignMarker(null)).join(' | ')} |`
        const rowLines = rows.map((row) => {
          const cells = header.map((_, i) => escapeTableCell(row[i] ?? ''))
          return `| ${cells.join(' | ')} |`
        })
        parts.push([headerLine, separator, ...rowLines].join('\n'))
        break
      }
      case 'code':
        parts.push(`\`\`\`${block.lang}\n${block.text}\n\`\`\``)
        break
      case 'hr':
        parts.push('---')
        break
      case 'blockquote':
        parts.push(
          block.text
            .split('\n')
            .map((line) => `> ${line}`)
            .join('\n'),
        )
        break
      case 'html':
        parts.push(block.raw)
        break
    }
    parts.push('')
  }
  // collapse multiple blank separators but trim trailing whitespace
  return `${parts
    .join('\n')
    .replace(/\n{3,}/g, '\n\n')
    .replace(/\n+$/, '')}\n`
}

function blockFromToken(token: Tokens.Generic): Block | null {
  switch (token.type) {
    case 'heading': {
      const t = token as Tokens.Heading
      const depth = Math.min(3, Math.max(1, t.depth)) as 1 | 2 | 3
      return { id: nextId(), kind: 'heading', depth, text: t.text }
    }
    case 'paragraph': {
      const t = token as Tokens.Paragraph
      return { id: nextId(), kind: 'paragraph', text: t.text }
    }
    case 'list': {
      const t = token as Tokens.List
      return {
        id: nextId(),
        kind: 'list',
        ordered: t.ordered,
        items: t.items.map((item) => item.text),
      }
    }
    case 'table': {
      const t = token as Tokens.Table
      const headerLen = t.header.length
      const rows = t.rows.map((row) => {
        const out: string[] = []
        for (let i = 0; i < headerLen; i += 1) {
          out.push(row[i]?.text ?? '')
        }
        return out
      })
      return {
        id: nextId(),
        kind: 'table',
        header: t.header.map((cell) => cell.text),
        rows,
      }
    }
    case 'code': {
      const t = token as Tokens.Code
      return {
        id: nextId(),
        kind: 'code',
        lang: t.lang ?? '',
        text: t.text,
      }
    }
    case 'hr':
      return { id: nextId(), kind: 'hr' }
    case 'blockquote': {
      const t = token as Tokens.Blockquote
      return { id: nextId(), kind: 'blockquote', text: t.text }
    }
    case 'html': {
      const t = token as Tokens.HTML
      return { id: nextId(), kind: 'html', raw: t.raw }
    }
    case 'space':
      return null
    default:
      return { id: nextId(), kind: 'html', raw: token.raw ?? '' }
  }
}

export function parseBlocks(body: string): Block[] {
  const trimmed = body.replace(/^\n+/, '')
  const tokens = marked.lexer(trimmed.length > 0 ? trimmed : '') as Tokens.Generic[]
  const blocks: Block[] = []
  for (const token of tokens) {
    const block = blockFromToken(token)
    if (block) blocks.push(block)
  }
  return blocks
}

export function applyInlineMarker(
  text: string,
  marker: 'bold' | 'italic' | 'code' | 'link',
  selection?: { start: number; end: number },
  linkUrl?: string,
): string {
  const start = selection?.start ?? text.length
  const end = selection?.end ?? text.length
  const before = text.slice(0, start)
  const selected = text.slice(start, end)
  const after = text.slice(end)
  switch (marker) {
    case 'bold':
      return `${before}**${selected || 'bold text'}**${after}`
    case 'italic':
      return `${before}*${selected || 'italic text'}*${after}`
    case 'code':
      return `${before}\`${selected || 'code'}\`${after}`
    case 'link':
      return `${before}[${selected || 'link text'}](${linkUrl ?? 'https://'})${after}`
  }
}
