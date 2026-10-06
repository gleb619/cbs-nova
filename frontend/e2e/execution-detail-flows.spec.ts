// Execution detail operator flows (T856).
// These specs use page.route('**/api/v1/**') to stub the BFF/backend surface
// so the signal and cancel flows can run offline without E2E_BACKEND. This is
// the opposite of the backendRequired fixture: specs here never skip, and the
// stubs make the responses deterministic. Use stubExecutionDetail() to add new
// offline detail specs.

import type { Page } from '@playwright/test'
import { expect, test } from './fixtures'

interface DeclaredSignal {
  name: string
  payloadType: string | null
}

interface StubTracker {
  cancelCalls: number
  signalCalls: number
}

interface StubOptions {
  declaredSignals?: DeclaredSignal[]
  signalState?: Record<string, boolean>
  cancelResponse?: Record<string, unknown>
}

function makeRunningExecution(overrides: Record<string, unknown> = {}) {
  return {
    id: 'exec-t856-running',
    entity: 'process-t856',
    entityType: 'Process',
    mode: 'RUN',
    status: 'Running',
    startedAt: '2026-01-01T00:00:00Z',
    input: {},
    output: null,
    trace: [],
    ...overrides,
  }
}

async function stubExecutionDetail(
  page: Page,
  execution: Record<string, unknown>,
  tracker: StubTracker,
  options: StubOptions = {},
) {
  const declaredSignals = options.declaredSignals ?? []
  const signalState = options.signalState ?? {}
  const cancelResponse = options.cancelResponse ?? { ...execution, status: 'Cancelled' }
  const executionId = String(execution.id)
  const entity = String(execution.entity)

  const signalFields: Record<string, unknown>[] = []
  for (const [index, signal] of declaredSignals.entries()) {
    signalFields.push({ path: `signals[${index}].name`, value: signal.name, type: 'string' })
    if (signal.payloadType) {
      signalFields.push({
        path: `signals[${index}].payloadType`,
        value: signal.payloadType,
        type: 'string',
      })
    }
  }

  return page.route('**/api/v1/**', async (route, request) => {
    const url = new URL(request.url())
    const method = request.method()
    const pathname = url.pathname

    if (method === 'GET' && pathname === `/api/v1/executions/${executionId}`) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(execution),
      })
    }
    if (
      method === 'GET' &&
      pathname === `/api/v1/dsl/processes/${encodeURIComponent(entity)}/structure`
    ) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ name: entity, type: 'process', fields: signalFields }),
      })
    }
    if (method === 'POST' && pathname === `/api/v1/dsl/signals/${executionId}`) {
      tracker.signalCalls += 1
      return route.fulfill({ status: 200 })
    }
    if (method === 'GET' && pathname === `/api/v1/dsl/queries/${executionId}`) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ signalState }),
      })
    }
    if (method === 'POST' && pathname === `/api/v1/executions/${executionId}/cancel`) {
      tracker.cancelCalls += 1
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(cancelResponse),
      })
    }

    return route.continue()
  })
}

test('signal panel: select declared chip, send, and render observed query state', async ({ page }) => {
  const tracker = { cancelCalls: 0, signalCalls: 0 }
  const execution = makeRunningExecution()
  await stubExecutionDetail(page, execution, tracker, {
    declaredSignals: [{ name: 'MySignal', payloadType: 'java.lang.String' }],
    signalState: { MySignal: true },
  })
  await page.goto(`/executions/${execution.id}`)

  await expect(page.getByTestId('signal-panel')).toBeVisible()
  await page.getByTestId('declared-signal-chip-MySignal').click()
  await expect(page.getByTestId('signal-name-input')).toHaveValue('MySignal')
  await page.getByTestId('send-signal-button').click()

  await expect(page.getByTestId('signal-result')).toContainText('Signal "MySignal" sent')
  await expect(page.getByTestId('signal-result-suffix')).toContainText('observed')
  await expect(page.locator('pre').filter({ hasText: '"MySignal": true' })).toBeVisible()
  expect(tracker.signalCalls).toBe(1)
})

test('undeclared signal name shows inline warning but send is still permitted', async ({ page }) => {
  const tracker = { cancelCalls: 0, signalCalls: 0 }
  const execution = makeRunningExecution()
  await stubExecutionDetail(page, execution, tracker, {
    declaredSignals: [{ name: 'KnownSignal', payloadType: null }],
    signalState: { UndeclaredSignal: true },
  })
  await page.goto(`/executions/${execution.id}`)

  await page.getByTestId('signal-name-input').fill('UndeclaredSignal')
  await expect(page.getByTestId('signal-unknown-warning')).toBeVisible()
  await page.getByTestId('send-signal-button').click()

  await expect(page.getByTestId('signal-result')).toContainText('Signal "UndeclaredSignal" sent')
  expect(tracker.signalCalls).toBe(1)
})

test('cancel modal confirms once, hits cancel endpoint, and closes', async ({ page }) => {
  const tracker = { cancelCalls: 0, signalCalls: 0 }
  const execution = makeRunningExecution()
  await stubExecutionDetail(page, execution, tracker)
  await page.goto(`/executions/${execution.id}`)

  await page.getByTestId('cancel-execution-button').click()
  await expect(page.getByTestId('cancel-confirmation-modal')).toBeVisible()

  await page.getByTestId('cancel-confirmation-modal-confirm').click()
  await expect(page.getByTestId('cancel-confirmation-modal')).not.toBeAttached()
  expect(tracker.cancelCalls).toBe(1)
})
