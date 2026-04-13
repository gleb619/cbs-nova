<template>
  <div class="bg-neutral-50">
    <!-- Top Header Bar -->
    <header class="bg-white border-b border-neutral-200 sticky top-0 z-10">
      <div class="max-w-4xl mx-auto px-4 py-3 flex items-center justify-between">
        <div class="flex items-center gap-3">
          <div class="w-8 h-8 bg-primary-600 rounded-sm flex items-center justify-center">
            <img src="/app-icon-64.png" alt="CBS Nova" class="w-10 h-10 object-contain non-touchable" draggable="false" />
          </div>
          <div>
            <h1 class="text-base font-semibold text-neutral-900 leading-tight text-left">CBS Nova</h1>
            <p class="text-xs text-neutral-500">Help & Documentation</p>
          </div>
        </div>
        <div class="flex items-center gap-2">
          <span class="inline-flex items-center gap-1.5 px-2.5 py-1 bg-success-50 text-success-700 rounded-sm text-xs font-medium">
            <span class="w-1.5 h-1.5 bg-success-500 rounded-full" />
            Dev Mode
          </span>
        </div>
      </div>
    </header>

    <main class="max-w-4xl mx-auto px-4 py-8">
      <!-- Quick Nav Cards -->
      <section class="mb-8">
        <h2 class="text-sm font-semibold text-neutral-500 uppercase tracking-wide mb-3">Quick Navigation</h2>
        <div class="grid grid-cols-1 sm:grid-cols-3 gap-3">
          <a
            href="/home"
            class="group flex items-center gap-3 p-4 bg-white border border-neutral-200 rounded-lg hover:border-primary-300 hover:shadow-sm transition-all text-left"
          >
            <div class="w-10 h-10 bg-primary-50 rounded-sm flex items-center justify-center group-hover:bg-primary-100 transition-colors">
              <svg class="w-5 h-5 text-primary-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                <path stroke-linecap="round" stroke-linejoin="round" d="M15.59 14.37a6 6 0 01-5.84 7.38v-4.8m5.84-2.58a14.98 14.98 0 006.16-12.12A14.98 14.98 0 009.631 8.41m5.96 5.96a14.926 14.926 0 01-5.841 2.58m-.119-8.54a6 6 0 00-7.381 5.84h4.8m2.581-5.84a14.927 14.927 0 00-2.58 5.84m2.699 2.7c-.103.021-.207.041-.311.06a15.09 15.09 0 01-2.448-2.448 14.9 14.9 0 01.06-.312m-2.24 2.39a4.493 4.493 0 00-1.757 4.306 4.493 4.493 0 004.306-1.758M16.5 9a1.5 1.5 0 11-3 0 1.5 1.5 0 013 0z" />
              </svg>
            </div>
            <div>
              <div class="text-sm font-semibold text-neutral-800">Launch App</div>
              <div class="text-xs text-neutral-500">Go to dashboard</div>
            </div>
          </a>

          <a
            href="/login"
            class="group flex items-center gap-3 p-4 bg-white border border-neutral-200 rounded-lg hover:border-success-300 hover:shadow-sm transition-all text-left"
          >
            <div class="w-10 h-10 bg-success-50 rounded-sm flex items-center justify-center group-hover:bg-success-100 transition-colors">
              <svg class="w-5 h-5 text-success-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                <path stroke-linecap="round" stroke-linejoin="round" d="M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z" />
              </svg>
            </div>
            <div>
              <div class="text-sm font-semibold text-neutral-800">Login</div>
              <div class="text-xs text-neutral-500">Sign in to your account</div>
            </div>
          </a>

          <a
            href="/settings"
            class="group flex items-center gap-3 p-4 bg-white border border-neutral-200 rounded-lg hover:border-neutral-300 hover:shadow-sm transition-all text-left"
          >
            <div class="w-10 h-10 bg-neutral-100 rounded-sm flex items-center justify-center group-hover:bg-neutral-200 transition-colors">
              <svg class="w-5 h-5 text-neutral-600" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="1.5">
                <path stroke-linecap="round" stroke-linejoin="round" d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.324.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 011.37.49l1.296 2.247a1.125 1.125 0 01-.26 1.431l-1.003.827c-.293.24-.438.613-.431.992a6.759 6.759 0 010 .255c-.007.378.138.75.43.99l1.005.828c.424.35.534.954.26 1.43l-1.298 2.247a1.125 1.125 0 01-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.57 6.57 0 01-.22.128c-.331.183-.581.495-.644.869l-.213 1.28c-.09.543-.56.941-1.11.941h-2.594c-.55 0-1.02-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 01-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 01-1.369-.49l-1.297-2.247a1.125 1.125 0 01.26-1.431l1.004-.827c.292-.24.437-.613.43-.992a6.932 6.932 0 010-.255c.007-.378-.138-.75-.43-.99l-1.004-.828a1.125 1.125 0 01-.26-1.43l1.297-2.247a1.125 1.125 0 011.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.087.22-.128.332-.183.582-.495.644-.869l.214-1.281z" />
                <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
              </svg>
            </div>
            <div>
              <div class="text-sm font-semibold text-neutral-800">Settings</div>
              <div class="text-xs text-neutral-500">App configuration</div>
            </div>
          </a>
        </div>
      </section>

      <!-- Project Overview -->
      <section class="mb-6">
        <h2 class="text-sm font-semibold text-neutral-500 uppercase tracking-wide mb-3">Project Overview</h2>
        <div class="bg-white border border-neutral-200 rounded-lg p-5">
          <div class="grid grid-cols-1 sm:grid-cols-2 gap-4">
            <div>
              <h3 class="text-xs font-medium text-neutral-400 uppercase mb-1.5">Architecture</h3>
              <p class="text-sm text-neutral-800">Hexagonal — frontend (Vue 3 SPA) + backend (Spring Boot)</p>
            </div>
            <div>
              <h3 class="text-xs font-medium text-neutral-400 uppercase mb-1.5">Frontend Stack</h3>
              <p class="text-sm text-neutral-800">Nuxt 3, Vue 3, TypeScript, TailwindCSS v4</p>
            </div>
            <div>
              <h3 class="text-xs font-medium text-neutral-400 uppercase mb-1.5">Backend Stack</h3>
              <p class="text-sm text-neutral-800">Spring Boot 4, Temporal, Kotlin DSL</p>
            </div>
            <div>
              <h3 class="text-xs font-medium text-neutral-400 uppercase mb-1.5">Authentication</h3>
              <p class="text-sm text-neutral-800">Local Auth (dev) or Keycloak (production)</p>
            </div>
          </div>
        </div>
      </section>

      <section class="mb-6">
        <h2 class="text-sm font-semibold text-neutral-500 uppercase tracking-wide mb-3">Development Setup</h2>
        <div class="bg-white border border-neutral-200 rounded-lg overflow-hidden">
          <div class="grid grid-cols-1 md:grid-cols-2 border-b border-neutral-100">
            <!-- Frontend Scripts -->
            <div class="p-5 border-r border-neutral-100">
              <h3 class="text-xs font-medium text-neutral-400 uppercase mb-3">Frontend Scripts</h3>
              <div class="space-y-2">
                <div class="flex items-center gap-3">
                  <code class="px-2 py-1 bg-primary-50 text-primary-700 rounded-sm text-xs font-mono font-medium min-w-[120px]">pnpm dev</code>
                  <span class="text-sm text-neutral-600">Start with Local Auth</span>
                </div>
                <div class="flex items-center gap-3">
                  <code class="px-2 py-1 bg-neutral-100 text-neutral-700 rounded-sm text-xs font-mono min-w-[120px]">pnpm serve</code>
                  <span class="text-sm text-neutral-600">Start with Keycloak Auth</span>
                </div>
                <div class="flex items-center gap-3">
                  <code class="px-2 py-1 bg-neutral-100 text-neutral-700 rounded-sm text-xs font-mono min-w-[120px]">pnpm build</code>
                  <span class="text-sm text-neutral-600">Build for production</span>
                </div>
              </div>
            </div>

            <!-- Backend Commands -->
            <div class="p-5 bg-neutral-50/30">
              <h3 class="text-xs font-medium text-neutral-400 uppercase mb-3">Backend Commands</h3>
              <div class="space-y-2">
                <div class="flex items-center gap-3">
                  <code class="px-2 py-1 bg-success-50 text-success-700 rounded-sm text-xs font-mono font-medium min-w-[120px]">./gradlew bootRun</code>
                  <span class="text-sm text-neutral-600">Run Spring Boot application</span>
                </div>
                <div class="flex items-center gap-3">
                  <code class="px-2 py-1 bg-neutral-100 text-neutral-700 rounded-sm text-xs font-mono min-w-[120px]">./gradlew test</code>
                  <span class="text-sm text-neutral-600">Run backend tests</span>
                </div>
                <div class="flex items-center gap-3">
                  <code class="px-2 py-1 bg-neutral-100 text-neutral-700 rounded-sm text-xs font-mono min-w-[120px]">./gradlew generateTsClient</code>
                  <span class="text-sm text-neutral-600">Generate TS Models</span>
                </div>
              </div>
            </div>
          </div>

          <!-- Server URLs -->
          <div class="p-5 border-b border-neutral-100">
            <h3 class="text-xs font-medium text-neutral-400 uppercase mb-3">Server URLs</h3>
            <div class="grid grid-cols-1 sm:grid-cols-3 gap-4">
              <div class="flex flex-col gap-1.5 p-3 bg-neutral-50 rounded-lg border border-neutral-100">
                <span class="text-[10px] font-bold text-neutral-400 uppercase tracking-tight">Frontend</span>
                <code class="text-xs font-mono text-primary-600 truncate">http://localhost:3000</code>
                <a href="http://localhost:3000" target="_blank" class="text-[10px] text-neutral-400 hover:text-primary-500 underline decoration-dotted">Open App &rarr;</a>
              </div>
              <div class="flex flex-col gap-1.5 p-3 bg-neutral-50 rounded-lg border border-neutral-100">
                <span class="text-[10px] font-bold text-neutral-400 uppercase tracking-tight">Backend API</span>
                <code class="text-xs font-mono text-success-600 truncate">http://localhost:7070</code>
                <a href="http://localhost:7070/actuator/health" target="_blank" class="text-[10px] text-neutral-400 hover:text-success-500 underline decoration-dotted">Check Health &rarr;</a>
              </div>
              <div class="flex flex-col gap-1.5 p-3 bg-neutral-50 rounded-lg border border-neutral-100">
                <span class="text-[10px] font-bold text-neutral-400 uppercase tracking-tight">OpenAPI Spec</span>
                <code class="text-xs font-mono text-amber-600 truncate">/v3/api-docs</code>
                <a href="http://localhost:7070/v3/api-docs" target="_blank" class="text-[10px] text-neutral-400 hover:text-amber-500 underline decoration-dotted">View JSON &rarr;</a>
              </div>
            </div>
          </div>
          <!-- Default Credentials -->
          <div class="p-5">
            <h3 class="text-xs font-medium text-neutral-400 uppercase mb-3">Default Credentials</h3>
            <div class="overflow-x-auto">
              <table class="w-full text-sm">
                <thead>
                  <tr class="border-b border-neutral-200">
                    <th class="text-center py-2 px-3 text-xs font-semibold text-neutral-500 uppercase">Username</th>
                    <th class="text-center py-2 px-3 text-xs font-semibold text-neutral-500 uppercase">Password</th>
                    <th class="text-center py-2 px-3 text-xs font-semibold text-neutral-500 uppercase">Role</th>
                  </tr>
                </thead>
                <tbody>
                  <tr class="border-b border-neutral-100">
                    <td class="py-2.5 px-3"><code class="px-1.5 py-0.5 bg-primary-50 text-primary-700 rounded-sm text-xs font-mono">admin1</code></td>
                    <td class="py-2.5 px-3"><code class="px-1.5 py-0.5 bg-primary-50 text-primary-700 rounded-sm text-xs font-mono">admin1</code></td>
                    <td class="py-2.5 px-3"><span class="inline-flex items-center px-2 py-0.5 bg-primary-50 text-primary-700 rounded-sm text-xs font-medium">ADMIN</span></td>
                  </tr>
                  <tr>
                    <td class="py-2.5 px-3"><code class="px-1.5 py-0.5 bg-neutral-100 text-neutral-700 rounded-sm text-xs font-mono">user1</code></td>
                    <td class="py-2.5 px-3"><code class="px-1.5 py-0.5 bg-neutral-100 text-neutral-700 rounded-sm text-xs font-mono">user1</code></td>
                    <td class="py-2.5 px-3"><span class="inline-flex items-center px-2 py-0.5 bg-neutral-100 text-neutral-700 rounded-sm text-xs font-medium">USER</span></td>
                  </tr>
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </section>

      <!-- API & Type Generation -->
      <section class="mb-6">
        <h2 class="text-sm font-semibold text-neutral-500 uppercase tracking-wide mb-3">API & Type Generation</h2>
        <div class="bg-white border border-neutral-200 rounded-lg p-5">
          <div class="flex items-start gap-4">
            <div>
              <h3 class="text-sm font-semibold text-neutral-800 mb-1">TypeScript Model Generation</h3>
              <p class="text-sm text-neutral-600 mb-3">
                To ensure type safety between frontend and backend, we use OpenAPI generator to create TypeScript models from the Spring Boot backend.
              </p>
              <div class="bg-neutral-50 rounded p-3 border border-neutral-100">
                <p class="text-xs text-neutral-500 mb-2">Run this command in the backend directory to update models:</p>
                <code class="text-xs font-mono text-primary-700">./gradlew generateTsClient</code>
              </div>
              <p class="text-xs text-neutral-400 mt-3">
                Configuration details can be found in <code class="px-1 py-0.5 bg-neutral-100 rounded text-[10px]">backend/build.gradle</code>.
              </p>
            </div>
          </div>
        </div>
      </section>
    </main>
  </div>
</template>

<script setup lang="ts">
</script>
