import { beforeEach, describe, expect, it, vi } from 'vitest'
import { useWorkbenchDoc } from '../useWorkbenchDoc'

describe('useWorkbenchDoc', () => {
  const readDoc = vi.fn<(name: string) => Promise<string | null>>()
  const writeDoc = vi.fn<(name: string, content: string) => Promise<unknown>>()
  const logError = vi.fn<(message: string, fields?: Record<string, unknown>) => void>()

  beforeEach(() => {
    readDoc.mockReset()
    writeDoc.mockReset()
    logError.mockReset()
    readDoc.mockResolvedValue(null)
    writeDoc.mockResolvedValue({ ok: true })
  })

  it('loads an existing doc and starts clean', async () => {
    readDoc.mockResolvedValueOnce('# Hello')
    const doc = useWorkbenchDoc({ readDoc, writeDoc, logError })

    await doc.loadDoc('LoanDisbursement')

    expect(readDoc).toHaveBeenCalledWith('LoanDisbursement')
    expect(doc.doc.value).toBe('# Hello')
    expect(doc.dirty.value).toBe(false)
  })

  it('falls back to a default frontmatter template when no doc exists', async () => {
    readDoc.mockResolvedValueOnce(null)
    const doc = useWorkbenchDoc({ readDoc, writeDoc, logError })

    await doc.loadDoc('LoanDisbursement')

    expect(doc.doc.value).toBe('---\nname: LoanDisbursement\ndescription: \n---\n')
    expect(doc.dirty.value).toBe(false)
  })

  it('falls back to the default template when the doc read fails', async () => {
    readDoc.mockRejectedValueOnce(new Error('boom'))
    const doc = useWorkbenchDoc({ readDoc, writeDoc, logError })

    await doc.loadDoc('BatchProcessing')

    expect(doc.doc.value).toContain('name: BatchProcessing')
    expect(logError).toHaveBeenCalled()
  })

  it('ignores a stale load when the selection changes mid-flight', async () => {
    readDoc.mockImplementationOnce(
      () => new Promise((resolve) => setTimeout(() => resolve('# Slow'), 20)),
    )
    readDoc.mockResolvedValueOnce('# Fast')
    const doc = useWorkbenchDoc({ readDoc, writeDoc, logError })

    const slow = doc.loadDoc('SlowConstruct')
    await doc.loadDoc('FastConstruct')
    await slow

    expect(doc.doc.value).toBe('# Fast')
  })

  it('marks dirty after an edit and clean again after save', async () => {
    readDoc.mockResolvedValueOnce('# Hello')
    const doc = useWorkbenchDoc({ readDoc, writeDoc, logError })
    await doc.loadDoc('LoanDisbursement')

    doc.updateDoc('# Hello v2')

    expect(doc.dirty.value).toBe(true)

    await doc.saveDoc()

    expect(writeDoc).toHaveBeenCalledWith('LoanDisbursement', '# Hello v2')
    expect(doc.dirty.value).toBe(false)
    expect(doc.saveStatus.value).toBe('saved')
    expect(doc.lastSavedAt.value).not.toBeNull()
  })

  it('does not save when clean', async () => {
    readDoc.mockResolvedValueOnce('# Hello')
    const doc = useWorkbenchDoc({ readDoc, writeDoc, logError })
    await doc.loadDoc('LoanDisbursement')

    await doc.saveDoc()

    expect(writeDoc).not.toHaveBeenCalled()
  })

  it('surfaces save failures without moving the baseline', async () => {
    readDoc.mockResolvedValueOnce('# Hello')
    writeDoc.mockRejectedValueOnce(new Error('write failed'))
    const doc = useWorkbenchDoc({ readDoc, writeDoc, logError })
    await doc.loadDoc('LoanDisbursement')
    doc.updateDoc('# Hello v2')

    await doc.saveDoc()

    expect(doc.saveStatus.value).toBe('error')
    expect(doc.dirty.value).toBe(true)
    expect(logError).toHaveBeenCalled()
  })

  it('clears the doc when the name is empty', async () => {
    readDoc.mockResolvedValueOnce('# Hello')
    const doc = useWorkbenchDoc({ readDoc, writeDoc, logError })
    await doc.loadDoc('LoanDisbursement')

    await doc.loadDoc('')

    expect(doc.doc.value).toBe('')
    expect(doc.dirty.value).toBe(false)
  })
})
