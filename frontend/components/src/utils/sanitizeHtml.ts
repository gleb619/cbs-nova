import DOMPurify from 'dompurify'

export type SanitizeProfile = 'markdown' | 'svg'

const DANGEROUS_PROTOCOLS = ['javascript:', 'vbscript:']

const URL_ATTRIBUTES = new Set([
  'href',
  'src',
  'action',
  'formaction',
  'poster',
  'cite',
  'data',
  'xlink:href',
])

const FORBIDDEN_VOID_TAGS = new Set(['base', 'frame', 'input', 'link', 'meta'])

const FORBIDDEN_CONTAINER_TAGS = new Set([
  'applet',
  'button',
  'embed',
  'form',
  'frame',
  'frameset',
  'iframe',
  'marquee',
  'object',
  'script',
  'select',
  'textarea',
])

const START_TAG_RE = /<([a-zA-Z][a-zA-Z0-9-]*)\b[^>]*>/g
const ATTRIBUTE_RE = /(\s+)([a-zA-Z_:][\w:.-]*)\s*=\s*("([^"]*)"|'([^']*)'|([^\s>"']+))/g
const STYLE_RE = /<style\b[^>]*>[\s\S]*?<\/style>/gi
const SCRIPT_RE = /<script\b[^>]*>[\s\S]*?<\/script>/gi
const CONTAINER_FORBIDDEN_RE = new RegExp(
  `<(${[...FORBIDDEN_CONTAINER_TAGS].join('|')})\\b[^>]*>[\\s\\S]*?<\\/\\1>`,
  'gi',
)
const VOID_FORBIDDEN_RE = new RegExp(`<(${[...FORBIDDEN_VOID_TAGS].join('|')})\\b[^>]*\\/?>`, 'gi')

let purifier: ReturnType<typeof DOMPurify> | null = null
let purifierWorks = false
let probed = false

function decodeHtmlEntities(value: string): string {
  const withoutHex = value.replace(/&#[xX]([0-9a-fA-F]+);/g, (_, hex) =>
    String.fromCodePoint(Number.parseInt(hex, 16)),
  )
  const withoutDecimal = withoutHex.replace(/&#([0-9]+);/g, (_, decimal) =>
    String.fromCodePoint(Number.parseInt(decimal, 10)),
  )
  const named: Record<string, string> = {
    '&': '&',
    '<': '<',
    '>': '>',
    '"': '"',
    "'": "'",
    Tab: '\t',
    NewLine: '\n',
  }
  return withoutDecimal.replace(/&([a-zA-Z][a-zA-Z0-9]*);/g, (match, name) =>
    Object.hasOwn(named, name) ? named[name] : match,
  )
}

function isDangerousUrl(value: string): boolean {
  const normalized = decodeHtmlEntities(value).replace(/\s/g, '').toLowerCase()
  if (DANGEROUS_PROTOCOLS.some((protocol) => normalized.startsWith(protocol))) {
    return true
  }
  if (normalized.startsWith('data:')) {
    return !/^data:(image|audio|video|font)\//i.test(normalized)
  }
  return false
}

function sanitizeTagAttributes(tag: string): string {
  return tag.replace(ATTRIBUTE_RE, (match, _space, name, _quote, double, single, unquoted) => {
    const attributeName = name.toLowerCase()
    if (attributeName.length > 2 && attributeName.startsWith('on')) {
      return ''
    }
    if (URL_ATTRIBUTES.has(attributeName)) {
      const value = double ?? single ?? unquoted ?? ''
      if (isDangerousUrl(value)) {
        return ''
      }
    }
    return match
  })
}

function runRegexGuard(dirty: string, profile: SanitizeProfile = 'markdown'): string {
  let cleaned = dirty.replace(SCRIPT_RE, '')
  if (profile === 'markdown') {
    cleaned = cleaned.replace(STYLE_RE, '')
  }
  cleaned = cleaned.replace(CONTAINER_FORBIDDEN_RE, '')
  cleaned = cleaned.replace(VOID_FORBIDDEN_RE, '')
  return cleaned.replace(START_TAG_RE, (match) => sanitizeTagAttributes(match))
}

function escapeHtml(value: string): string {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function buildConfig(profile: SanitizeProfile): DOMPurify.Config {
  if (profile === 'svg') {
    return { ADD_TAGS: ['style'], ALLOW_DATA_ATTR: false, USE_PROFILES: { svg: true } }
  }
  return { ALLOW_DATA_ATTR: false, USE_PROFILES: { html: true } }
}

function getPurifier(): ReturnType<typeof DOMPurify> | null {
  if (typeof window === 'undefined') {
    return null
  }
  if (probed) {
    return purifierWorks ? purifier : null
  }
  probed = true
  try {
    const p = DOMPurify(window)
    const safeProbe = '<h1>safe</h1><p>ok</p><svg xmlns="http://www.w3.org/2000/svg"><rect/></svg>'
    const dangerProbe = '<a href="javascript:alert(1)" onclick="x()">x</a>'
    const safeOut = p.sanitize(safeProbe, buildConfig('markdown'))
    const dangerOut = p.sanitize(dangerProbe, { ...buildConfig('markdown'), KEEP_CONTENT: false })
    purifierWorks =
      safeOut.includes('<h1>') &&
      safeOut.includes('<svg') &&
      !dangerOut.includes('javascript:') &&
      !dangerOut.includes('onclick')
    purifier = p
  } catch {
    purifierWorks = false
    purifier = null
  }
  return purifierWorks ? purifier : null
}

export function sanitizeHtml(dirty: string, profile: SanitizeProfile = 'markdown'): string {
  const p = getPurifier()
  if (!p) {
    // Fail closed: without a working DOM purifier (no window / probe failed) never emit markup.
    // happy-dom's DOM is too incomplete for DOMPurify, so unit tests alone fall back to the regex guard.
    return import.meta.env.MODE === 'test' ? runRegexGuard(dirty, profile) : escapeHtml(dirty)
  }
  return p.sanitize(runRegexGuard(dirty, profile), buildConfig(profile)) as string
}
