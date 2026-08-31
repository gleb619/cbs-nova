import { createError, defineEventHandler, getQuery, sendRedirect } from 'h3'
import { useAuthConfig } from '~/server/utils/config'
import {
  clearOidcTxn,
  exchangeCode,
  readOidcTxn,
  sameOriginRedirect,
  writeSession,
} from '~/server/utils/oidcSession'

export default defineEventHandler(async (event) => {
  const config = useAuthConfig()
  if (!config.enabled) {
    throw createError({ statusCode: 404, statusMessage: 'Not found' })
  }

  const query = getQuery(event)
  const code = typeof query.code === 'string' ? query.code : undefined
  const state = typeof query.state === 'string' ? query.state : undefined
  const txn = readOidcTxn(event)

  if (!txn || !state || txn.state !== state || !code) {
    throw createError({ statusCode: 403, statusMessage: 'Invalid OIDC state' })
  }

  const tokenResponse = await exchangeCode(code, txn.verifier)
  writeSession(event, tokenResponse, config.callbackUrl)
  clearOidcTxn(event, config.callbackUrl)

  return sendRedirect(event, sameOriginRedirect(txn.redirect, '/'), 302)
})
