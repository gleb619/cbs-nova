import { useFetch } from '#app'
import type { AdminInfo } from '~/types'

export function useAdminInfo() {
  return useFetch<AdminInfo>('/api/v1/info', {
    key: 'admin-info',
    default: () => ({}),
  })
}
