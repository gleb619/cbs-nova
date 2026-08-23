import { useClientLogger } from '@cbs/admin-ui-plugin/composables/useClientLogger'
import { useRouter } from 'nuxt/app'

export default defineNuxtPlugin(() => {
  const log = useClientLogger('runtime')
  const router = useRouter()

  router.afterEach((to, from) => {
    log.info('navigated', {
      from: from.fullPath,
      to: to.fullPath,
      name: String(to.name ?? ''),
    })
  })
})
