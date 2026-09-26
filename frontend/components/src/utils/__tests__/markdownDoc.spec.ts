import { describe, expect, it } from 'vitest'
import { parseMarkdownDoc, serializeMarkdownDoc } from '../markdownDoc'

describe('parseMarkdownDoc', () => {
  it('parses frontmatter and body', () => {
    const md = '---\nname: BatchProcessing\ndescription: Sums values\n---\n\n# Title\n\nSome text\n'

    expect(parseMarkdownDoc(md)).toEqual({
      frontmatter: { name: 'BatchProcessing', description: 'Sums values' },
      body: '# Title\n\nSome text\n',
    })
  })

  it('parses quoted scalar values', () => {
    const md = "---\nname: \"A: B\"\ndescription: 'it''s'\n---\n\nbody\n"

    expect(parseMarkdownDoc(md)).toEqual({
      frontmatter: { name: 'A: B', description: "it's" },
      body: 'body\n',
    })
  })

  it('returns empty frontmatter when there is no fence', () => {
    expect(parseMarkdownDoc('# Title\n\ntext\n')).toEqual({
      frontmatter: {},
      body: '# Title\n\ntext\n',
    })
  })

  it('returns empty frontmatter for an unterminated fence', () => {
    const md = '---\nname: X\n\nbody\n'

    expect(parseMarkdownDoc(md)).toEqual({ frontmatter: {}, body: md })
  })

  it('keeps unknown frontmatter keys', () => {
    const md = '---\nname: X\nextra: keep me\n---\n\nbody\n'

    expect(parseMarkdownDoc(md).frontmatter).toEqual({ name: 'X', extra: 'keep me' })
  })

  it('strips a BOM prefix', () => {
    const md = '\uFEFF---\nname: X\n---\n\nbody\n'

    expect(parseMarkdownDoc(md).frontmatter).toEqual({ name: 'X' })
  })
})

describe('serializeMarkdownDoc', () => {
  it('serializes frontmatter and body', () => {
    const md = serializeMarkdownDoc({
      frontmatter: { name: 'BatchProcessing', description: 'Sums values' },
      body: '# Title\n\nSome text\n',
    })

    expect(md).toBe(
      '---\nname: BatchProcessing\ndescription: Sums values\n---\n\n# Title\n\nSome text\n',
    )
  })

  it('omits the frontmatter block when there are no fields', () => {
    expect(serializeMarkdownDoc({ frontmatter: {}, body: '# Title\n' })).toBe('# Title\n')
    expect(serializeMarkdownDoc({ frontmatter: {}, body: '' })).toBe('')
  })

  it('quotes values that are not plain-scalar safe', () => {
    const md = serializeMarkdownDoc({
      frontmatter: { name: 'A: B', description: 'trailing ' },
      body: 'body',
    })

    expect(md).toBe('---\nname: "A: B"\ndescription: "trailing "\n---\n\nbody\n')
  })
})

describe('round trip', () => {
  it.each([
    '---\nname: BatchProcessing\ndescription: Sums values\n---\n\n# Title\n\nText with `code` and, commas\n',
    '---\nname: "Quoted: name"\n---\n\nbody\n',
    '# No frontmatter at all\n\nbody\n',
  ])('parse ∘ serialize preserves %j', (md) => {
    expect(serializeMarkdownDoc(parseMarkdownDoc(md))).toBe(md)
  })
})
