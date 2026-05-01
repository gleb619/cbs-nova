<template>
  <div class="min-h-full bg-neutral-50 flex flex-col items-center justify-center p-6 lg:p-12 overflow-hidden relative">
    <!-- Abstract Background Elements -->
    <div class="absolute inset-0 pointer-events-none overflow-hidden">
      <div class="absolute top-[-10%] right-[-10%] w-[40%] h-[40%] bg-primary-500/5 rounded-full blur-[120px]" />
      <div class="absolute bottom-[-10%] left-[-10%] w-[40%] h-[40%] bg-primary-600/5 rounded-full blur-[120px]" />
    </div>

    <div class="w-full max-w-4xl mx-auto px-4 py-8">
      <!-- App Identity -->
      <div class="flex flex-col items-center gap-6 mb-12">
        <div class="w-16 h-16 rounded-2xl flex items-center justify-center shadow-2xl shadow-primary-200 animate-in zoom-in duration-700 p-2">
          <img src="/app-icon-64.png" alt="CBS Nova" class="w-10 h-10 object-contain non-touchable" draggable="false" />
        </div>
        <h1 class="text-5xl font-black text-neutral-900 tracking-tighter sm:text-6xl animate-in slide-in-from-bottom-4 duration-700">
          CBS Nova
        </h1>
      </div>

      <!-- Description -->
      <p class="text-lg text-neutral-600 leading-relaxed mb-12 px-4 animate-in slide-in-from-bottom-6 duration-700 delay-100">
        <span class="font-bold text-neutral-900">Business Orchestration Engine</span> for core banking operations. 
        Built on <span class="text-primary-600 font-semibold">Java 25</span>, <span class="text-primary-600 font-semibold">Spring Boot</span>, and <span class="text-primary-600 font-semibold">Temporal</span> workflows, 
        with PostgreSQL persistence, Java DSL rules engine, and a Vue/Nuxt.js admin UI.
      </p>

      <!-- Actions -->
      <div class="grid grid-cols-1 sm:grid-cols-2 gap-4 animate-in slide-in-from-bottom-8 duration-700 delay-200">
        <router-link to="/help" class="px-8 py-4 border border-neutral-200 text-neutral-900 font-bold rounded-xl hover:bg-neutral-50 hover:border-neutral-300 transition-all shadow-sm active:scale-[0.98] text-center">
          View Documentation
        </router-link>
        <router-link to="/home" class="px-8 py-4 border border-neutral-200 text-primary-600 font-bold rounded-xl
        hover:bg-neutral-50 hover:border-neutral-300 transition-all shadow-xl shadow-neutral-200 active:scale-[0.98] flex items-center justify-center gap-2">
          Launch Dashboard
          <svg class="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
            <path stroke-linecap="round" stroke-linejoin="round" d="M13 7l5 5m0 0l-5 5m5-5H6" />
          </svg>
        </router-link>
      </div>

      <!-- Micro Copy -->
      <div class="mt-16 text-neutral-400 text-sm font-medium animate-in fade-in duration-1000 delay-500">
        Next-generation financial infrastructure.
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
</script>
