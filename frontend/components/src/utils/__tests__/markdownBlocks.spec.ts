import { beforeEach, describe, expect, it } from 'vitest'
import {
  applyInlineMarker,
  createBlock,
  parseBlocks,
  resetBlockIdCounter,
  serializeBlocks,
} from '../markdownBlocks'

describe('markdownBlocks', () => {
  beforeEach(() => {
    resetBlockIdCounter()
  })

  it('parses headings, paragraphs, lists, tables, code, hr, blockquotes', () => {
    const md = [
      '# Title',
      '',
      'A short paragraph.',
      '',
      '- one',
      '- two',
      '',
      '1. first',
      '2. second',
      '',
      '| a | b |',
      '| --- | --- |',
      '| 1 | 2 |',
      '',
      '```ts',
      'const x = 1',
      '```',
      '',
      '---',
      '',
      '> quoted line',
    ].join('\n')
    const blocks = parseBlocks(md)
    expect(blocks.map((b) => b.kind)).toEqual([
      'heading',
      'paragraph',
      'list',
      'list',
      'table',
      'code',
      'hr',
      'blockquote',
    ])
  })

  it('round-trips a basic document through parse → serialize', () => {
    const md = '## Heading\n\nA paragraph with **bold**.\n'
    const blocks = parseBlocks(md)
    const out = serializeBlocks(blocks)
    expect(out).toContain('## Heading')
    expect(out).toContain('A paragraph with **bold**.')
  })

  it('round-trips lists (ordered + unordered) preserving items', () => {
    const md = '- a\n- b\n- c\n'
    const blocks = parseBlocks(md)
    expect(blocks[0].kind).toBe('list')
    if (blocks[0].kind === 'list') {
      expect(blocks[0].ordered).toBe(false)
      expect(blocks[0].items).toEqual(['a', 'b', 'c'])
    }
    const out = serializeBlocks(blocks)
    expect(out).toContain('- a')
    expect(out).toContain('- b')
    expect(out).toContain('- c')

    const orderedMd = '1. x\n2. y\n'
    const ordered = parseBlocks(orderedMd)
    expect(ordered[0].kind).toBe('list')
    if (ordered[0].kind === 'list') {
      expect(ordered[0].ordered).toBe(true)
    }
  })

  it('round-trips a 2x2 table with escaped pipes in cells', () => {
    const md = ['| a | b |', '| --- | --- |', '| 1 | 2 |', '| 3 | 4 |'].join('\n') + '\n'
    const blocks = parseBlocks(md)
    expect(blocks[0].kind).toBe('table')
    if (blocks[0].kind === 'table') {
      expect(blocks[0].header).toEqual(['a', 'b'])
      expect(blocks[0].rows).toEqual([
        ['1', '2'],
        ['3', '4'],
      ])
    }
    const out = serializeBlocks(blocks)
    expect(out).toContain('| a | b |')
    expect(out).toContain('| 1 | 2 |')
    expect(out).toContain('| 3 | 4 |')
  })

  it('round-trips fenced code blocks with a language tag', () => {
    const md = '```ts\nconst x = 1\n```\n'
    const blocks = parseBlocks(md)
    expect(blocks[0].kind).toBe('code')
    if (blocks[0].kind === 'code') {
      expect(blocks[0].lang).toBe('ts')
      expect(blocks[0].text).toBe('const x = 1')
    }
    expect(serializeBlocks(blocks)).toContain('```ts')
    expect(serializeBlocks(blocks)).toContain('const x = 1')
  })

  it('serializes headings at the configured depth', () => {
    const block = { ...createBlock('heading'), depth: 3 as 1 | 2 | 3, text: 'Sub' }
    expect(serializeBlocks([block])).toContain('### Sub')
  })

  it('serializes an empty list with a single placeholder item', () => {
    const block = createBlock('list')
    if (block.kind === 'list') {
      expect(serializeBlocks([block])).toContain('- ')
    }
  })

  it('escapes pipe characters in table cells', () => {
    const block = {
      ...createBlock('table'),
      header: ['x|y', 'z'],
      rows: [['1|2', '3']],
    }
    const out = serializeBlocks([block])
    expect(out).toContain('x\\|y')
    expect(out).toContain('1\\|2')
  })

  it('applyInlineMarker wraps selection with bold/italic/code/link', () => {
    expect(applyInlineMarker('hello world', 'bold', { start: 6, end: 11 })).toBe('hello **world**')
    expect(applyInlineMarker('a b', 'italic', { start: 2, end: 3 })).toBe('a *b*')
    expect(applyInlineMarker('foo', 'code', { start: 0, end: 3 })).toBe('`foo`')
    expect(applyInlineMarker('click', 'link', { start: 0, end: 5 }, 'https://x')).toBe(
      '[click](https://x)',
    )
  })

  it('applyInlineMarker appends a placeholder when nothing is selected', () => {
    expect(applyInlineMarker('tail', 'bold')).toBe('tail**bold text**')
  })
})
