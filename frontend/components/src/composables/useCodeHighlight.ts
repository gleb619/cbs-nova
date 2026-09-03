import Prism from 'prismjs'
import { computed, type Ref, toValue } from 'vue'
import 'prismjs/components/prism-java'

type LanguageSource = string | Ref<string> | (() => string)

function escapeHtml(text: string): string {
  return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
}

export function useCodeHighlight(code: Ref<string>, language: LanguageSource = 'java') {
  const highlightedHtml = computed(() => {
    const value = code.value
    if (!value) return '&nbsp;'

    const lang = toValue(language)
    const grammar = Prism.languages[lang]
    if (!grammar) return escapeHtml(value)

    return Prism.highlight(value, grammar, lang)
  })

  return { highlightedHtml }
}
