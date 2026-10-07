import { describe, expect, it } from 'vitest'
import { sanitizeHtml } from '../sanitizeHtml'

const XSS_PAYLOADS = [
  '<img src=x onerror="alert(1)" onload="x()">',
  '<script>alert(1)</script>',
  '<p onclick="alert(1)">text</p>',
  '<a href="javascript:alert(1)">click</a>',
  '<a href="jav\u0009ascript:alert(1)">click</a>',
  '<svg onload="alert(1)" class="mermaid"><rect/></svg>',
  '<svg><a xlink:href="javascript:alert(1)">X</a></svg>',
  '<iframe src="javascript:alert(1)"></iframe>',
  '<form action="javascript:alert(1)"><button>submit</button></form>',
  '<input onfocus="alert(1)" autofocus>',
]

describe('sanitizeHtml', () => {
  it.each(XSS_PAYLOADS)('neutralizes payload: %s', (dirty) => {
    const clean = sanitizeHtml(dirty)
    expect(clean).not.toMatch(/<script\b/i)
    expect(clean).not.toMatch(/\son[a-z]+\s*=/i)
    expect(clean.toLowerCase()).not.toContain('javascript:')
  })

  it('preserves benign markdown html', () => {
    const html =
      '<h1>Title</h1><p class="intro">Hello <strong>world</strong>.</p><table><tr><th>A</th><td>B</td></tr></table><pre><code class="language-js">const x = 1</code></pre><a href="https://example.com">link</a><img src="https://example.com/x.png" alt="x">'
    const clean = sanitizeHtml(html)
    expect(clean).toContain('<h1>Title</h1>')
    expect(clean).toContain('class="intro"')
    expect(clean).toContain('class="language-js"')
    expect(clean).toContain('href="https://example.com"')
    expect(clean).toContain('src="https://example.com/x.png"')
  })

  it('preserves benign svg output', () => {
    const svg =
      '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100" class="mermaid"><style>.cls{fill:red}</style><g><rect class="cls" width="10" height="10"/></g></svg>'
    const clean = sanitizeHtml(svg, 'svg')
    expect(clean).toContain('<svg')
    expect(clean).toContain('class="mermaid"')
    expect(clean).toContain('viewBox="0 0 100 100"')
    expect(clean).toContain('<style>.cls{fill:red}</style>')
  })
})

describe('sanitizeHtml hardening', () => {
  it('markdown profile strips <style> (CSS injection)', () => {
    const out = sanitizeHtml('<p>x</p><style>body{display:none}</style>', 'markdown')
    expect(out).not.toContain('<style')
  })
})
