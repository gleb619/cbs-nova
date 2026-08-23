import { type Logger, useLogger } from '@cbs/components/composables'

const noopLogger: Logger = {
  trace: () => {},
  debug: () => {},
  info: () => {},
  warn: () => {},
  error: () => {},
}

export function useClientLogger(scope: string): Logger {
  if (typeof window === 'undefined') return noopLogger
  return useLogger(scope)
}
