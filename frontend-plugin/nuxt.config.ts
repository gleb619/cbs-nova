// https://nuxt.com/docs/api/configuration/nuxt-config
import { fileURLToPath } from 'node:url';

export default defineNuxtConfig({
  compatibilityDate: '2025-01-01',
  css: [fileURLToPath(new URL('./assets/main.scss', import.meta.url))],
});
