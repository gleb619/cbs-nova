export interface MarkdownDoc {
  frontmatter: Record<string, string>
  body: string
}

const FRONTMATTER_FENCE = '---'
const FRONTMATTER_KEY_PATTERN = /^([A-Za-z0-9_-]+):(?:\s+(.*))?$/

export function parseMarkdownDoc(content: string): MarkdownDoc {
  const normalized = content.replace(/^\uFEFF/, '')
  const lines = normalized.split('\n')
  const hasFence = lines[0]?.trim() === FRONTMATTER_FENCE
  const closingIndex = hasFence
    ? lines.findIndex((line, index) => index > 0 && line.trim() === FRONTMATTER_FENCE)
    : -1
  if (!hasFence || closingIndex === -1) {
    return { frontmatter: {}, body: normalized }
  }
  const frontmatter: Record<string, string> = {}
  for (const line of lines.slice(1, closingIndex)) {
    const match = FRONTMATTER_KEY_PATTERN.exec(line)
    if (!match) continue
    frontmatter[match[1]] = unquoteYamlScalar(match[2] ?? '')
  }
  const body = lines
    .slice(closingIndex + 1)
    .join('\n')
    .replace(/^\n+/, '')
  return { frontmatter, body }
}

export function serializeMarkdownDoc(doc: MarkdownDoc): string {
  const body = doc.body.replace(/\n+$/, '')
  const entries = Object.entries(doc.frontmatter).filter(([, value]) => value !== undefined)
  if (entries.length === 0) {
    return body ? `${body}\n` : ''
  }
  const frontmatter = entries.map(([key, value]) => `${key}: ${formatYamlScalar(value)}`).join('\n')
  return `---\n${frontmatter}\n---\n\n${body}\n`
}

function unquoteYamlScalar(raw: string): string {
  const value = raw.trim()
  if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
    try {
      return JSON.parse(value) as string
    } catch {
      return value.slice(1, -1)
    }
  }
  if (value.length >= 2 && value.startsWith("'") && value.endsWith("'")) {
    return value.slice(1, -1).replace(/''/g, "'")
  }
  return value
}

function formatYamlScalar(value: string): string {
  const isPlainSafe =
    value !== '' &&
    /^[A-Za-z0-9_-]/.test(value) &&
    !value.includes('\n') &&
    !/: /.test(value) &&
    !/ #/.test(value) &&
    value.trimEnd() === value
  return isPlainSafe ? value : JSON.stringify(value)
}
