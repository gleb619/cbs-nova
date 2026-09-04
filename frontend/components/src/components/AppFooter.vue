<script setup lang="ts">
import { computed } from 'vue'
import type { BuildInfo, GitInfo } from '../types/buildInfo'
import AppFooterCopyright from './footer/AppFooterCopyright.vue'
import AppFooterGitInfo from './footer/AppFooterGitInfo.vue'
import AppFooterLinks from './footer/AppFooterLinks.vue'

export interface FooterLink {
  label: string
  href: string
}

const props = withDefaults(
  defineProps<{
    copyright?: string
    year?: number
    docsBaseUrl?: string
    extraLinks?: FooterLink[]
    buildInfo?: BuildInfo | null
    gitInfo?: GitInfo | null
  }>(),
  {
    copyright: 'CBS Nova',
    year: () => new Date().getFullYear(),
    buildInfo: () => null,
    gitInfo: () => null,
  },
)

const resolvedDocsBase = computed(() => {
  const raw = props.docsBaseUrl ?? '/docs/'
  return raw.endsWith('/') ? raw : `${raw}/`
})

const docLinks = computed<FooterLink[]>(() => [
  { label: 'Architecture', href: `${resolvedDocsBase.value}architecture.md` },
  { label: 'UI docs', href: `${resolvedDocsBase.value}frontend/index.md` },
  { label: 'Runner', href: `${resolvedDocsBase.value}frontend/runner.md` },
])

const links = computed<FooterLink[]>(() => [...docLinks.value, ...(props.extraLinks ?? [])])

const buildName = computed(() => props.buildInfo?.name ?? props.buildInfo?.artifact ?? '')
const versionText = computed(() => props.buildInfo?.version ?? '')
const buildTime = computed(() => formatDate(props.buildInfo?.time))

const commitTime = computed(() => formatDate(props.gitInfo?.commit?.time))

function formatDate(value?: string): string {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleString(undefined, {
    dateStyle: 'short',
    timeStyle: 'short',
  })
}
</script>

<template>
  <footer
    data-testid="app-footer"
    class="shrink-0 border-t border-neutral-200 bg-neutral-50 px-4 py-2 text-sm text-neutral-600"
  >
    <div class="flex flex-col items-center justify-between gap-2 md:flex-row">
      <AppFooterCopyright
        :year="year"
        :copyright="copyright"
        :version-text="versionText"
        :build-name="buildName"
        :build-time="buildTime"
        :priority="1"
      />

      <AppFooterLinks :links="links" :priority="3" />

      <AppFooterGitInfo :git-info="gitInfo" :commit-time="commitTime" :priority="2" />
    </div>
  </footer>
</template>
