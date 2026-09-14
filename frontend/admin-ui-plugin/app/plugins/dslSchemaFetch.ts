import { DSL_SCHEMA_FETCH_KEY } from '@cbs/components'
import { defineNuxtPlugin } from 'nuxt/app'

/**
 * Provide the DSL schema fetcher so @cbs/components can stay free of $fetch.
 * The components package injects DSL_SCHEMA_FETCH_KEY and calls it with the
 * full schemas URL; this plugin satisfies that contract with Nuxt's $fetch.
 */
export default defineNuxtPlugin((nuxtApp) => {
  nuxtApp.vueApp.provide(DSL_SCHEMA_FETCH_KEY, (url: string) => ($fetch as (url: string) => Promise<unknown>)(url))
})
