export function useBackendConfig() {
  const config = useRuntimeConfig()
  return {
    baseUrl: (config.backendBaseUrl as string) ?? 'http://localhost:8090',
    apiKey: (config.backendApiKey as string) ?? '',
    timeoutMs: Number(config.backendTimeoutMs ?? 10000),
  }
}
