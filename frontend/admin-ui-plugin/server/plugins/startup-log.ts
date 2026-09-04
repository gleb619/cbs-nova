import { defineNitroPlugin, useRuntimeConfig } from 'nitropack/runtime'

export default defineNitroPlugin(() => {
  const config = useRuntimeConfig()
  const baseUrl = (config.backendBaseUrl as string) ?? 'http://localhost:8090'
  const timeoutMs = Number(config.backendTimeoutMs ?? 10000)
  const apiKey = (config.backendApiKey as string) ?? ''
  console.log(
    `[BFF] Backend API: ${baseUrl} (timeout ${timeoutMs}ms, apiKey: ${apiKey ? 'configured' : 'none'})`,
  )
})
