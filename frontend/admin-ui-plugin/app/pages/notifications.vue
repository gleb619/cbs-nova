<script setup lang="ts">
import { useNotifications } from '@cbs/admin-ui-plugin/composables/useNotifications'
import { DslNotificationRuleList } from '@cbs/components'
import { onMounted } from 'vue'

const {
  rules,
  loading,
  error,
  fireLog,
  fireLogLoading,
  fireLogError,
  testing,
  testResult,
  load,
  loadFireLog,
  create,
  update,
  toggleEnabled,
  remove,
  test,
} = useNotifications()

onMounted(() => {
  void load()
  void loadFireLog(0)
})
</script>

<template>
  <div class="p-6 space-y-4 h-full flex flex-col">
    <header>
      <h1 class="text-2xl font-bold text-neutral-900">Notifications</h1>
      <p class="text-sm text-neutral-600">
        Rules that match domain events and fan out to webhook, email, Slack, or PagerDuty sinks.
      </p>
    </header>

    <DslNotificationRuleList
      :rules="rules"
      :loading="loading"
      :error="error"
      :testing="testing"
      :test-result="testResult"
      :fire-log="fireLog"
      :fire-log-loading="fireLogLoading"
      :fire-log-error="fireLogError"
      @create="create"
      @update="update"
      @delete="remove"
      @toggle="toggleEnabled"
      @test="test"
      @load-fire-log="(params) => loadFireLog(params.offset)"
    />
  </div>
</template>
