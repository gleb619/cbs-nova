export default defineNuxtConfig({
  devtools: { enabled: true },
  modules: ['@pinia/nuxt'],
  css: ['~/assets/css/main.css'],
  compatibilityDate: '2024-04-03',
  runtimeConfig: {
    backendBaseUrl: process.env.BACKEND_BASE_URL ?? 'http://localhost:8090',
    backendApiKey: process.env.BACKEND_API_KEY ?? '',
    public: {
      appName: 'CBS Nova Admin',
    },
  },
})
