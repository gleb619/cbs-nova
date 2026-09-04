<script setup lang="ts">
import { useAuth } from '@cbs/admin-ui-plugin/composables/useAuth'
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import {
  AppFooter,
  AppShell,
  AppSidebarToggle,
  DslSavedDraftsWidget,
  useSavedDrafts,
} from '@cbs/components'
import { navigateTo, useRoute } from 'nuxt/app'
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { NuxtLink } from '#components'

function handleSaveHotkey(event: KeyboardEvent) {
  if ((event.ctrlKey || event.metaKey) && event.key.toLowerCase() === 's') {
    event.preventDefault()
    window.dispatchEvent(new CustomEvent('dsl:save'))
  }
}

onMounted(() => window.addEventListener('keydown', handleSaveHotkey))
onBeforeUnmount(() => window.removeEventListener('keydown', handleSaveHotkey))

const route = useRoute()
const { data: info } = useAdminInfo()
const { enabled: authEnabled, authenticated, user, login, logout } = useAuth()

const dslApi = useDslApi()

// Shared with the workbench page through the per-app drafts store. The layout
// owns the fetcher so the widget has data on every route; the workbench page
// registers the select handler while it is mounted.
const {
  drafts,
  loading: draftsLoading,
  error: draftsError,
  selectedName: draftsSelectedName,
  refresh: refreshDrafts,
  select: selectDraft,
} = useSavedDrafts({ fetcher: () => dslApi.listDrafts() })

async function onDraftSelect(name: string) {
  // The workbench handles selection itself when mounted. Anywhere else, send
  // the user there and let the page pick the draft up from the query.
  if (selectDraft(name)) return
  await navigateTo({ path: '/dsl-workbench', query: { draft: name } })
}

const navItems = computed(() => [
  { to: '/', label: 'Dashboard', icon: '🏠', isActive: route.path === '/' },
  {
    to: '/dsl-workbench',
    label: 'DSL Workbench',
    icon: '⚙️',
    isActive: route.path === '/dsl-workbench',
  },
  { to: '/runner', label: 'Runner', icon: '▶️', isActive: route.path === '/runner' },
  { to: '/schedules', label: 'Schedules', icon: '🕒', isActive: route.path === '/schedules' },
  {
    to: '/executions',
    label: 'Executions',
    icon: '📋',
    isActive: route.path.startsWith('/executions'),
  },
])

const docsBaseUrl = computed(() => {
  const git = info.value?.git
  const origin = git?.remote?.origin?.url
  if (!origin) return undefined

  const branch = git?.branch ?? 'main'

  const gitMatch = origin.match(/^git@github\.com:([^/]+)\/(.+?)(?:\.git)?$/)
  if (gitMatch) {
    return `https://github.com/${gitMatch[1]}/${gitMatch[2]}/blob/${branch}/docs/`
  }

  const httpsMatch = origin.match(/^https:\/\/github\.com\/([^/]+)\/(.+?)(?:\.git)?$/)
  if (httpsMatch) {
    return `https://github.com/${httpsMatch[1]}/${httpsMatch[2]}/blob/${branch}/docs/`
  }

  return undefined
})

const displayName = computed(
  () => user.value?.preferred_username ?? user.value?.name ?? user.value?.email ?? 'User',
)
</script>

<template>
  <AppShell
    :nav-items="navItems"
    :link-component="NuxtLink"
    title="CBS Nova"
    short-title="N"
    active-class="bg-primary-500 text-white"
    :pad="route.meta.pad === true"
  >
    <template #toggle>
      <AppSidebarToggle />
    </template>
    <template #brand>
      <span class="text-neutral-800 font-semibold">CBS Nova Admin</span>
    </template>
    <template #widgets>
      <DslSavedDraftsWidget
        :drafts="drafts"
        :loading="draftsLoading"
        :error="draftsError"
        :selected-name="draftsSelectedName"
        auto-load
        @select="onDraftSelect"
        @refresh="refreshDrafts"
      />
    </template>
    <template #trailing>
      <div v-if="authEnabled" class="flex items-center gap-3">
        <button
          v-if="!authenticated"
          type="button"
          data-testid="auth-signin"
          class="text-sm text-primary-600 hover:text-primary-700"
          @click="login()"
        >
          Sign in
        </button>
        <template v-else>
          <span data-testid="auth-user" class="text-sm text-neutral-700">{{ displayName }}</span>
          <button
            type="button"
            data-testid="auth-signout"
            class="text-sm text-primary-600 hover:text-primary-700"
            @click="logout()"
          >
            Sign out
          </button>
        </template>
      </div>
    </template>
    <template #footer>
      <AppFooter
        copyright="CBS Nova"
        :build-info="info?.build"
        :git-info="info?.git"
        :docs-base-url="docsBaseUrl"
      />
    </template>
    <slot />
  </AppShell>
</template>
