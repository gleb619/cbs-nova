import { createError, defineEventHandler, getQuery, sendRedirect } from 'h3'
import { useAuthConfig } from '~/server/utils/config'
import {
  createPkcePair,
  discoverOidc,
  randomState,
  sameOriginRedirect,
  writeOidcTxn,
} from '~/server/utils/oidcSession'

export default defineEventHandler(async (event) => {
  const config = useAuthConfig()
  if (!config.enabled) {
    throw createError({ statusCode: 404, statusMessage: 'Not found' })
  }

  const query = getQuery(event)
  const redirect = sameOriginRedirect(query.redirect, '/')
  const state = randomState()
  const { verifier, challenge } = await createPkcePair()

  writeOidcTxn(event, { state, verifier, redirect }, config.callbackUrl)

  const { authorization_endpoint } = await discoverOidc(config.issuer)
  const authUrl = new URL(authorization_endpoint)
  authUrl.searchParams.set('response_type', 'code')
  authUrl.searchParams.set('client_id', config.clientId)
  authUrl.searchParams.set('redirect_uri', config.callbackUrl)
  authUrl.searchParams.set('scope', 'openid profile email')
  authUrl.searchParams.set('state', state)
  authUrl.searchParams.set('code_challenge', challenge)
  authUrl.searchParams.set('code_challenge_method', 'S256')

  return sendRedirect(event, authUrl.toString(), 302)
})
