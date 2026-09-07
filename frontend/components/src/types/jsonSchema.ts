export type JsonSchemaType = 'string' | 'number' | 'boolean' | 'object' | 'array' | 'null' | 'any'

export interface JsonSchema {
  $schema?: string
  type?: JsonSchemaType
  properties?: Record<string, JsonSchema>
  required?: string[]
  items?: JsonSchema
  enum?: unknown[]
  default?: unknown
  // deferred: oneOf, anyOf, allOf, $ref, format, minLength, pattern, minimum, maximum
}
