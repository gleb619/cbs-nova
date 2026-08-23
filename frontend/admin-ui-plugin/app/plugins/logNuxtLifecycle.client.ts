import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'

const LIFECYCLE_NAMES = new Set([
  'app:created',
  'app:beforeMount',
  'app:mounted',
  'app:rendered',
  'app:suspense:resolve',
  'vue:setup',
  'page:loading:start',
  'page:loading:end',
  'page:finish',
  'link:prefetch',
])

export default defineNuxtPlugin((nuxtApp) => {
  const log = useClientLogger('nuxt')
  const startTimes = new Map<string, number[]>()

  nuxtApp.hooks.beforeEach((event: { name: string }) => {
    if (!LIFECYCLE_NAMES.has(event.name)) return
    const times = startTimes.get(event.name) ?? []
    times.push(performance.now())
    startTimes.set(event.name, times)
  })

  nuxtApp.hooks.afterEach((event: { name: string; args: unknown[] }) => {
    if (!LIFECYCLE_NAMES.has(event.name)) return
    const times = startTimes.get(event.name) ?? []
    const start = times.shift()
    if (times.length === 0) {
      startTimes.delete(event.name)
    }
    const duration = start !== undefined ? `${(performance.now() - start).toFixed(3)}ms` : '??'
    log.trace(`${event.name}: ${duration}`, event.args)
  })
})
