import { initializeAuthConfig } from '../auth/application/AuthConfig';

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig();
  initializeAuthConfig(config.public.localAuth);
});
