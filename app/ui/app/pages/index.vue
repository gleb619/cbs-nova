<script setup lang="ts">
const runtimeConfig = useRuntimeConfig()
const portalName = runtimeConfig.public.portalName || 'CBS Operator Portal'
</script>

<template>
  <div class="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-12">
    <div class="max-w-2xl">
      <h1 class="text-4xl font-bold text-neutral-800 mb-4">
        Welcome to {{ portalName }}
      </h1>
      <p class="text-lg text-neutral-600 mb-8">
        This is a realistic client integration of the CBS Nova admin UI. The portal owns its own
        landing page and shell, while the orchestration admin interface is mounted under
        <code class="text-primary-700 font-semibold">/nova-admin</code>.
      </p>
      <div class="flex flex-wrap gap-4">
        <NuxtLink
          to="/nova-admin"
          class="inline-flex items-center px-5 py-2.5 rounded-lg bg-primary-500 text-white font-medium hover:bg-primary-600"
        >
          Open CBS Nova Admin
        </NuxtLink>
        <NuxtLink
          to="/nova-admin/runner"
          class="inline-flex items-center px-5 py-2.5 rounded-lg bg-white border border-neutral-300 text-neutral-700 font-medium hover:bg-neutral-50"
        >
          Go to Runner
        </NuxtLink>
      </div>
    </div>

    <div class="mt-16 grid grid-cols-1 sm:grid-cols-3 gap-6">
      <div class="bg-white rounded-lg border border-neutral-200 p-6 shadow-sm">
        <div class="text-2xl mb-2">⚙️</div>
        <h2 class="font-semibold text-neutral-800 mb-1">DSL Workbench</h2>
        <p class="text-sm text-neutral-500">
          Author and validate orchestration definitions.
        </p>
      </div>
      <div class="bg-white rounded-lg border border-neutral-200 p-6 shadow-sm">
        <div class="text-2xl mb-2">▶️</div>
        <h2 class="font-semibold text-neutral-800 mb-1">Runner</h2>
        <p class="text-sm text-neutral-500">
          Run, preview, and explain DSL flows against the backend.
        </p>
      </div>
      <div class="bg-white rounded-lg border border-neutral-200 p-6 shadow-sm">
        <div class="text-2xl mb-2">📋</div>
        <h2 class="font-semibold text-neutral-800 mb-1">Executions</h2>
        <p class="text-sm text-neutral-500">
          Inspect past and in-flight workflow executions.
        </p>
      </div>
    </div>
  </div>
</template>
