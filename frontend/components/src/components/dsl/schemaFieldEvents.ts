import type { InjectionKey } from 'vue'

export interface SchemaFieldEventBus {
  emitClearError: (name: string) => void
  onClearError: (listener: (name: string) => void) => () => void
}

export const SCHEMA_FIELD_EVENTS_KEY: InjectionKey<SchemaFieldEventBus> =
  Symbol('schema-field-events')

export function createSchemaFieldEventBus(): SchemaFieldEventBus {
  const listeners = new Set<(name: string) => void>()
  return {
    emitClearError: (name) => {
      for (const listener of listeners) {
        listener(name)
      }
    },
    onClearError: (listener) => {
      listeners.add(listener)
      return () => {
        listeners.delete(listener)
      }
    },
  }
}
