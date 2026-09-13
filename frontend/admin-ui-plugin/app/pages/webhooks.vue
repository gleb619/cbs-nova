<script setup lang="ts">
import { useDslApi } from '@cbs/admin-ui-plugin/composables/useDslApi'
import { WebhookDeliveriesPanel } from '@cbs/components'

const dslApi = useDslApi()
</script>

<template>
  <div class="p-6 space-y-4 h-full flex flex-col" data-testid="webhooks-page">
    <header>
      <h1 class="text-2xl font-bold text-neutral-900">Webhooks</h1>
      <p class="text-sm text-neutral-600">
        Delivery outcomes for run-completion webhook subscriptions, newest first.
      </p>
    </header>

    <div class="flex-1 min-h-0 bg-white border border-neutral-200 rounded-lg overflow-hidden">
      <WebhookDeliveriesPanel :fetch-page="dslApi.fetchWebhookDeliveries" />
    </div>
  </div>
</template>
