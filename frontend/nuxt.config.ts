// https://nuxt.com/docs/api/configuration/nuxt-config
import { fileURLToPath } from 'node:url';
import tailwindcss from '@tailwindcss/vite';
import tsconfigPaths from 'vite-tsconfig-paths';

export default defineNuxtConfig({
  extends: ['../frontend-plugin'],
  compatibilityDate: '2025-01-01',
  devtools: { enabled: true },

  app: {
    head: {
      charset: 'utf-8',
      viewport: 'width=device-width, initial-scale=1',
      title: 'CBS Nova',
      titleTemplate: '%s | CBS Nova',
      meta: [
        { name: 'description', content: 'CBS Nova - Cloud Banking Solution' },
        { name: 'theme-color', content: '#ffffff' },
        { name: 'robots', content: 'index, follow' },
        { property: 'og:title', content: 'CBS Nova' },
        { property: 'og:type', content: 'website' },
        { property: 'og:description', content: 'Cloud Banking Solution' },
      ],
      link: [
        { rel: 'icon', type: 'image/x-icon', href: '/favicon.ico' },
        { rel: 'apple-touch-icon', sizes: '180x180', href: '/apple-touch-icon.png' },
        { rel: 'icon', type: 'image/png', sizes: '32x32', href: '/favicon-32x32.png' },
        { rel: 'icon', type: 'image/png', sizes: '16x16', href: '/favicon-16x16.png' },
        { rel: 'manifest', href: '/site.webmanifest' },
      ],
      htmlAttrs: {
        lang: 'en',
      },
      bodyAttrs: {
        class: 'bg-neutral-50 text-neutral-900 antialiased',
      },
    },
  },

  ssr: false,

  css: [fileURLToPath(new URL('./src/app/assets/main.css', import.meta.url))],

  dir: {
    pages: '',
    plugins: 'src/app/plugins',
  },

  // Prevent Nuxt from auto-importing Vue SPA components
  components: false,

  devServer: {
    port: 3000,
  },

  hooks: {
    listen(server) {
      server.on('listening', () => {
        const addr = server.address();
        const port = typeof addr === 'object' && addr ? addr.port : 3000;
        // biome-ignore lint/suspicious/noConsole: dev-only
        console.log(`\n  ➜  Help page: http://localhost:${port}/help\n`);
      });
    },
  },

  vite: {
    plugins: [tsconfigPaths(), tailwindcss()],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src/app', import.meta.url)),
        '@shared': fileURLToPath(new URL('./src/shared', import.meta.url)),
      },
    },
  },

  nitro: {
    devProxy: {
      '/api': {
        target: process.env.SPRING_BOOT_URL ?? 'http://localhost:7070',
        changeOrigin: true,
      },
    },
  },

  runtimeConfig: {
    public: {
      apiBase: process.env.SPRING_BOOT_URL ?? 'http://localhost:7070',
      localAuth: process.env.NUXT_PUBLIC_LOCAL_AUTH === 'true',
    },
  },

  typescript: {
    strict: true,
  },
});
