export type JsonSchemaType = 'string' | 'number' | 'boolean' | 'object' | 'array' | 'null' | 'any'

export interface JsonSchema {
  $schema?: string
  type?: JsonSchemaType
  properties?: Record<string, JsonSchema>
  required?: string[]
  items?: JsonSchema
  enum?: unknown[]
  default?: unknown
  inputSchema?: JsonSchema | null
  outputSchema?: JsonSchema | null
  inputType?: string | null
  outputType?: string | null
  // deferred: oneOf, anyOf, allOf, $ref, format, minLength, pattern, minimum, maximum
}
