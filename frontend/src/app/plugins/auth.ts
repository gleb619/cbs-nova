import { initializeAuthConfig } from '@/auth/AuthConfig';
import { defineNuxtPlugin, useRuntimeConfig } from 'nuxt/app';

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig();
  initializeAuthConfig(config.public.localAuth);
});
