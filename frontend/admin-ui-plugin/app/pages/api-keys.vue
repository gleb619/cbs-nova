<script setup lang="ts">
import { useApiKeys } from '@cbs/admin-ui-plugin/composables/useApiKeys'
import { DslApiKeyList, type CreatedApiKey } from '@cbs/components'
import { onMounted, ref } from 'vue'

const { keys, loading, error, revokingId, load, create, revoke } = useApiKeys()

const createdSecret = ref<string | null>(null)

onMounted(() => void load())

async function onCreate(label: string) {
  createdSecret.value = null
  try {
    const result: CreatedApiKey = await create(label)
    createdSecret.value = result.key
  } catch {
    // error already surfaced by the composable
  }
}

async function onRevoke(id: number) {
  try {
    await revoke(id)
  } catch {
    // error already surfaced by the composable
  }
}

function onDismissSecret() {
  createdSecret.value = null
}
</script>

<template>
  <div class="p-6 space-y-4 h-full flex flex-col" data-testid="api-keys-page">
    <header>
      <h1 class="text-2xl font-bold text-neutral-900">API keys</h1>
      <p class="text-sm text-neutral-600">
        Manage long-lived keys for DSL authentication and provisioning.
      </p>
    </header>

    <DslApiKeyList
      :api-keys="keys"
      :loading="loading"
      :error="error"
      :revoking-id="revokingId"
      :created-secret="createdSecret"
      @create="onCreate"
      @revoke="onRevoke"
      @dismiss-secret="onDismissSecret"
    />
  </div>
</template>
