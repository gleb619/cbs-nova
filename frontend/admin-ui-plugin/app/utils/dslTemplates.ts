export interface DslTemplate {
  id: string
  label: string
  description: string
  body: string
}

export const DSL_TEMPLATES: DslTemplate[] = [
  {
    id: 'plain-process',
    label: 'Plain process',
    description: 'A minimal process that returns a successful result.',
    body: JSON.stringify(
      {
        name: 'PlainProcess',
        type: 'Process',
        status: 'Draft',
        version: '1',
        taskQueue: 'default',
        execute: { result: 'success' },
      },
      null,
      2,
    ),
  },
  {
    id: 'saga',
    label: 'Saga transaction',
    description: 'A transaction with compensation steps for failure recovery.',
    body: JSON.stringify(
      {
        name: 'SagaOrder',
        type: 'Transaction',
        status: 'Draft',
        version: '1',
        taskQueue: 'saga',
        steps: [
          { helper: 'reserveInventory' },
          { helper: 'chargePayment' },
        ],
        compensation: {
          steps: [{ helper: 'refundPayment' }, { helper: 'releaseInventory' }],
        },
      },
      null,
      2,
    ),
  },
  {
    id: 'http-pipeline',
    label: 'HTTP pipeline',
    description: 'A function that calls an external HTTP endpoint via the httpCall helper.',
    body: JSON.stringify(
      {
        name: 'HttpPipeline',
        type: 'Function',
        status: 'Draft',
        version: '1',
        taskQueue: 'default',
        steps: [
          {
            helper: 'httpCall',
            input: { url: 'https://example.com/api', method: 'GET' },
          },
        ],
      },
      null,
      2,
    ),
  },
  {
    id: 'retry-policy',
    label: 'Retry policy',
    description: 'A function wrapped with a retry policy for resilient execution.',
    body: JSON.stringify(
      {
        name: 'RetryFunction',
        type: 'Function',
        status: 'Draft',
        version: '1',
        taskQueue: 'default',
        retry: { maxAttempts: 3, initialIntervalSeconds: 1, backoffCoefficient: 2 },
        steps: [{ helper: 'unreliableApi' }],
      },
      null,
      2,
    ),
  },
]
