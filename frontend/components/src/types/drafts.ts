export interface DraftsMetadata {
  draftCount: number
  workbenchPath: string
  sizeMb: number | null
  gitBranch: string | null
  gitEnabled: boolean
  statusCacheTtlSeconds: number
  historyLimit: number
}
