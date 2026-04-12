<template>
  <div class="min-h-full bg-neutral-100 flex items-center justify-center px-4">
    <div class="w-full max-w-sm">
      <!-- Card -->
      <div class="bg-white rounded-2xl shadow-md border border-neutral-200 overflow-hidden">
        <!-- Top accent bar -->
        <div class="h-1 bg-gradient-to-r from-primary-500 to-primary-700" />

        <div class="px-7 py-8">
          <!-- Logo + Title -->
          <div class="mb-7 flex flex-col items-center text-center gap-1">
            <div class="w-10 h-10 rounded-xl bg-primary-600 flex items-center justify-center shadow shadow-primary-300 mb-2">
              <img src="/app-icon-64.png" alt="CBS Nova" class="w-10 h-10 object-contain non-touchable" draggable="false" />
            </div>
            <h1 class="text-xl font-extrabold text-neutral-900 tracking-tight">CBS Nova</h1>
          </div>

          <form @submit.prevent="handleSubmit" class="space-y-3" novalidate>
            <!-- Error -->
            <Transition
              enter-active-class="transition ease-out duration-200"
              enter-from-class="opacity-0 -translate-y-1"
              enter-to-class="opacity-100 translate-y-0"
              leave-active-class="transition ease-in duration-150"
              leave-from-class="opacity-100"
              leave-to-class="opacity-0"
            >
              <div
                v-if="errorMessage"
                class="flex items-center gap-2 px-3 py-2 bg-red-50 border border-red-200 rounded-lg text-xs text-red-600 font-medium"
              >
                <svg class="w-3.5 h-3.5 shrink-0" fill="currentColor" viewBox="0 0 20 20">
                  <path fill-rule="evenodd" d="M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" clip-rule="evenodd" />
                </svg>
                {{ errorMessage }}
              </div>
            </Transition>

            <!-- Username -->
            <div class="space-y-1">
              <label for="username" class="text-[11px] font-semibold text-neutral-500 uppercase tracking-wider">
                Username
              </label>
              <input
                id="username"
                v-model="username"
                type="text"
                required
                autocomplete="username"
                data-testid="username-input"
                class="w-full px-3 py-2.5 rounded-lg border border-neutral-200 bg-neutral-50 text-sm text-neutral-900 outline-none transition-all focus:bg-white focus:border-primary-500 focus:ring-2 focus:ring-primary-500/20 placeholder:text-neutral-300"
                placeholder="Enter username"
              />
            </div>

            <!-- Password -->
            <div class="space-y-1">
              <label for="password" class="text-[11px] font-semibold text-neutral-500 uppercase tracking-wider">
                Password
              </label>
              <div class="relative">
                <input
                  id="password"
                  v-model="password"
                  :type="showPassword ? 'text' : 'password'"
                  required
                  autocomplete="current-password"
                  data-testid="password-input"
                  class="w-full pl-3 pr-9 py-2.5 rounded-lg border border-neutral-200 bg-neutral-50 text-sm text-neutral-900 outline-none transition-all focus:bg-white focus:border-primary-500 focus:ring-2 focus:ring-primary-500/20 placeholder:text-neutral-300"
                  placeholder="Enter password"
                />
                <button
                  type="button"
                  tabindex="-1"
                  @click="showPassword = !showPassword"
                  class="absolute right-2.5 top-1/2 -translate-y-1/2 text-neutral-400 hover:text-neutral-600 transition-colors"
                >
                  <!-- Eye / Eye-off -->
                  <svg v-if="!showPassword" class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                    <path stroke-linecap="round" stroke-linejoin="round" d="M2.458 12C3.732 7.943 7.523 5 12 5c4.477 0 8.268 2.943 9.542 7-1.274 4.057-5.065 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                  </svg>
                  <svg v-else class="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                    <path stroke-linecap="round" stroke-linejoin="round" d="M13.875 18.825A10.05 10.05 0 0112 19c-4.477 0-8.268-2.943-9.542-7a9.97 9.97 0 012.34-3.898M6.878 6.878A9.97 9.97 0 0112 5c4.477 0 8.268 2.943 9.542 7a9.97 9.97 0 01-1.357 2.664M6.878 6.878L3 3m3.878 3.878l10.243 10.243M3 3l18 18" />
                  </svg>
                </button>
              </div>
            </div>

            <!-- Submit -->
            <button
              type="submit"
              :disabled="isSubmitting"
              data-testid="submit-button"
              class="mt-1 relative w-full py-2.5 rounded-lg text-sm font-bold transition-all bg-primary-600 text-white hover:bg-primary-700 active:scale-[0.98] disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100 shadow-sm shadow-primary-300"
            >
              <span :class="{ 'opacity-0': isSubmitting }" class="flex items-center justify-center gap-2">
                Sign In
                <svg class="w-4 h-4 transition-transform group-hover:translate-x-0.5" fill="none" viewBox="0 0 24 24" stroke="currentColor" stroke-width="2">
                  <path stroke-linecap="round" stroke-linejoin="round" d="M14 5l7 7m0 0l-7 7m7-7H3" />
                </svg>
              </span>
              <div v-if="isSubmitting" class="absolute inset-0 flex items-center justify-center">
                <div class="w-4 h-4 border-2 border-white/30 border-t-white rounded-full animate-spin" />
              </div>
            </button>
          </form>
        </div>

        <!-- Footer -->
        <div class="px-7 py-3 bg-neutral-50 border-t border-neutral-100 flex items-center justify-center gap-1.5">
          <svg class="w-3 h-3 text-neutral-400" fill="currentColor" viewBox="0 0 20 20">
            <path fill-rule="evenodd" d="M5 9V7a5 5 0 0110 0v2a2 2 0 012 2v5a2 2 0 01-2 2H5a2 2 0 01-2-2v-5a2 2 0 012-2zm8-2v2H7V7a3 3 0 016 0z" clip-rule="evenodd" />
          </svg>
          <p class="text-[10px] text-neutral-400 font-medium">Use <code>admin1</code>/<code>admin1</code> for local
          development</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { defineComponent } from 'vue';
import { useAuth } from './useAuth';

export default defineComponent({
  name: 'LoginPageVue',
  data() {
    return {
      username: '',
      password: '',
      showPassword: false,
      errorMessage: '',
      isSubmitting: false,
    };
  },
  methods: {
    async handleSubmit() {
      if (!this.username || !this.password) return;

      this.errorMessage = '';
      this.isSubmitting = true;

      try {
        await useAuth().login({ username: this.username, password: this.password });
        //TODO: Redirect to Previous URL (a Return URL in Authentication Flow), like deep linking
        this.$router.push('/home');
      } catch (error: unknown) {
        const msg = error instanceof Error ? error.message : '';
        this.errorMessage =
          msg === 'invalid_credentials'
            ? 'Invalid username or password.'
            : 'Login failed. Please try again.';
      } finally {
        this.isSubmitting = false;
      }
    },
  },
});
</script>