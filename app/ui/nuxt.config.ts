export default defineNuxtConfig({
  compatibilityDate: '2024-04-03',
  devtools: { enabled: true },
  pages: true,

  modules: ['@cbs/admin-ui-plugin'],

  adminUiPlugin: {
    routePrefix: '/nova-admin',
    appName: 'CBS Nova — Operator Portal',
    backendBaseUrl: process.env.BACKEND_BASE_URL || 'http://localhost:8090',
    backendApiKey: process.env.BACKEND_API_KEY,
  },

  runtimeConfig: {
    public: {
      portalName: 'CBS Operator Portal',
    },
  },

  postcss: {
    plugins: {
      tailwindcss: {},
      autoprefixer: {},
    },
  },

  build: {
    transpile: ['@cbs/admin-ui-plugin'],
  },

  nitro: {
    transpile: ['@cbs/admin-ui-plugin'],
  },
})
