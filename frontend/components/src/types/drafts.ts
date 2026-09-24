export interface DraftsMetadata {
  draftCount: number
  sourcePath: string
  sizeMb: number | null
  gitBranch: string | null
  gitEnabled: boolean
  statusCacheTtlSeconds: number
  historyLimit: number
}
