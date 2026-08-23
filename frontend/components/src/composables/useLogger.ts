export type LogLevel = 'trace' | 'debug' | 'info' | 'warn' | 'error'

const LOG_LEVEL_KEY = 'cbs-log-level'
const LEVEL_RANK: Record<LogLevel, number> = {
  trace: -1,
  debug: 0,
  info: 1,
  warn: 2,
  error: 3,
}

function defaultMinLevel(): LogLevel {
  try {
    if (import.meta.env?.PROD) return 'warn'
  } catch {
    // ignore
  }
  return 'debug'
}

export interface UseLoggerOptions {
  minLevel?: LogLevel
}

export function useLogger(scope: string, options: UseLoggerOptions = {}) {
  const stored = typeof window !== 'undefined' ? window.localStorage.getItem(LOG_LEVEL_KEY) : null
  const minLevel: LogLevel = options.minLevel ?? (stored as LogLevel | null) ?? defaultMinLevel()
  const minRank = LEVEL_RANK[minLevel] ?? LEVEL_RANK.debug

  function log(level: LogLevel, message: string, data?: unknown) {
    if (LEVEL_RANK[level] < minRank) return
    const fn =
      level === 'trace'
        ? console.debug
        : (console[level] as (msg: string, ...rest: unknown[]) => void)
    const prefix = `[${scope}]`
    if (data === undefined) {
      fn(`${prefix} ${message}`)
      return
    }
    fn(`${prefix} ${message}`, data)
  }

  return {
    trace: (message: string, data?: unknown) => log('trace', message, data),
    debug: (message: string, data?: unknown) => log('debug', message, data),
    info: (message: string, data?: unknown) => log('info', message, data),
    warn: (message: string, data?: unknown) => log('warn', message, data),
    error: (message: string, data?: unknown) => log('error', message, data),
  }
}

export type Logger = ReturnType<typeof useLogger>
