import type { GitInfo } from '../types/buildInfo'

export function buildDocsBaseUrl(git: GitInfo | undefined): string | undefined {
  const origin = git?.remote?.origin?.url
  if (!origin) return undefined

  const branch = git?.branch ?? 'main'

  const gitMatch = origin.match(/^git@github\.com:([^/]+)\/(.+?)(?:\.git)?$/)
  if (gitMatch) {
    return `https://github.com/${gitMatch[1]}/${gitMatch[2]}/blob/${branch}/docs/`
  }

  const httpsMatch = origin.match(/^https:\/\/github\.com\/([^/]+)\/(.+?)(?:\.git)?$/)
  if (httpsMatch) {
    return `https://github.com/${httpsMatch[1]}/${httpsMatch[2]}/blob/${branch}/docs/`
  }

  return undefined
}
