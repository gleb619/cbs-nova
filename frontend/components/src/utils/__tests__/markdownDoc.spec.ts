import { describe, expect, it } from 'vitest'
import {
  createDefaultMarkdownDoc,
  parseMarkdownDoc,
  renameDocTitle,
  serializeMarkdownDoc,
} from '../markdownDoc'

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

describe('createDefaultMarkdownDoc', () => {
  it('puts the name in the frontmatter and in a "# " body heading', () => {
    const md = createDefaultMarkdownDoc('LoanDisbursement')

    expect(md).toBe('---\nname: LoanDisbursement\ndescription: ""\n---\n\n# LoanDisbursement\n')
    expect(parseMarkdownDoc(md)).toEqual({
      frontmatter: { name: 'LoanDisbursement', description: '' },
      body: '# LoanDisbursement\n',
    })
  })

  it('keeps a supplied description', () => {
    expect(createDefaultMarkdownDoc('Foo', 'Sums values')).toContain('description: Sums values')
  })

  it('trims the name', () => {
    expect(createDefaultMarkdownDoc('  Foo  ')).toBe(
      '---\nname: Foo\ndescription: ""\n---\n\n# Foo\n',
    )
  })

  it('returns an empty doc for a blank name', () => {
    expect(createDefaultMarkdownDoc('   ')).toBe('')
    expect(createDefaultMarkdownDoc('')).toBe('')
  })
})

describe('renameDocTitle', () => {
  it('retitles the leading heading', () => {
    expect(renameDocTitle('# Old\n\nbody\n', 'Old', 'New')).toBe('# New\n\nbody\n')
  })

  it('leaves the body alone when the first line is a different heading', () => {
    expect(renameDocTitle('# Something else\n\nbody\n', 'Old', 'New')).toBe(
      '# Something else\n\nbody\n',
    )
  })

  it('ignores blank, missing or unchanged names', () => {
    expect(renameDocTitle('# Old\n', '', 'New')).toBe('# Old\n')
    expect(renameDocTitle('# Old\n', 'Old', '  ')).toBe('# Old\n')
    expect(renameDocTitle('# Old\n', 'Old', 'Old')).toBe('# Old\n')
  })

  it('does not retitle a heading that is not the first line', () => {
    expect(renameDocTitle('intro\n# Old\n', 'Old', 'New')).toBe('intro\n# Old\n')
  })
})
