<script setup lang="ts">
import { computed } from 'vue'
import type { BuildInfo, GitInfo } from '../types/buildInfo'

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

const branch = computed(() => props.gitInfo?.branch ?? '')
const commitShort = computed(() => props.gitInfo?.commit?.id ?? '')
const commitMessage = computed(() => props.gitInfo?.commit?.message?.short ?? '')
const commitTime = computed(() => formatDate(props.gitInfo?.commit?.time))
const isDirty = computed(() => props.gitInfo?.dirty)

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
    class="shrink-0 border-t border-neutral-200 bg-neutral-50 px-4 py-2 text-sm text-neutral-600"
  >
    <div class="flex flex-col items-center justify-between gap-2 md:flex-row">
      <div class="flex flex-wrap items-center gap-x-2 gap-y-1">
        <span>© {{ year }} {{ copyright }}</span>
        <span v-if="versionText" class="text-neutral-400">
          | {{ buildName }} v{{ versionText }}
        </span>
        <span v-if="buildTime" class="text-neutral-400" title="Build time">
          ({{ buildTime }})
        </span>
      </div>

      <nav class="flex flex-wrap items-center justify-center gap-x-4 gap-y-1">
        <a
          v-for="link in links"
          :key="link.label"
          :href="link.href"
          target="_blank"
          rel="noopener noreferrer"
          class="hover:text-primary-600"
        >
          {{ link.label }}
        </a>
      </nav>

      <div
        v-if="gitInfo"
        class="flex flex-wrap items-center justify-center gap-x-2 gap-y-1 text-xs text-neutral-500"
      >
        <span v-if="branch" class="font-medium">{{ branch }}</span>
        <code
          v-if="commitShort"
          class="rounded bg-neutral-100 px-1 font-mono"
          :title="gitInfo.commit?.['id.full'] ?? ''"
        >
          {{ commitShort }}
        </code>
        <span v-if="commitMessage" class="max-w-xs truncate" :title="commitMessage">
          {{ commitMessage }}
        </span>
        <span v-if="commitTime" title="Commit time">({{ commitTime }})</span>
        <span v-if="isDirty" class="text-warning-600">(dirty)</span>
      </div>
    </div>
  </footer>
</template>
