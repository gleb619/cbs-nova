export interface BuildInfo {
  artifact?: string
  group?: string
  name?: string
  version?: string
  time?: string
}

export interface GitCommit {
  id?: string
  'id.full'?: string
  time?: string
  message?: {
    short?: string
    full?: string
  }
  user?: {
    name?: string
    email?: string
  }
}

export interface GitInfo {
  branch?: string
  commit?: GitCommit
  dirty?: boolean
  remote?: {
    origin?: {
      url?: string
    }
  }
  tags?: string
  'total.commit.count'?: string
}

export interface AdminInfo {
  build?: BuildInfo
  git?: GitInfo
}
