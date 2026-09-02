<script setup lang="ts">
import { useSchedules } from '@cbs/admin-ui-plugin/composables/useSchedules'
import { DslScheduleList } from '@cbs/components'
import { onMounted } from 'vue'

const { schedules, loading, error, load, create, remove } = useSchedules()

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="p-6 space-y-4 h-full flex flex-col">
    <header>
      <h1 class="text-2xl font-bold text-gray-900">Schedules</h1>
      <p class="text-sm text-gray-600">
        Cron-driven Temporal schedules that start a published DSL workflow.
      </p>
    </header>

    <DslScheduleList
      :schedules="schedules"
      :loading="loading"
      :error="error"
      @create="create"
      @delete="remove"
    />
  </div>
</template>
