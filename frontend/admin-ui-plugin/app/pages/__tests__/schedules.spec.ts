import { mount, flushPromises } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { defineComponent, h, nextTick, type Ref } from 'vue'
import SchedulesPage from '../schedules.vue'
import type { CreateSchedulePayload, ScheduleSummary } from '@cbs/components'

// ---------------------------------------------------------------------------
// Harness for `useSchedules()` consumed by the schedules page.
// ---------------------------------------------------------------------------

interface SchedulesHarness {
  schedules: Ref<ScheduleSummary[]>
  loading: Ref<boolean>
  error: Ref<string | null>
  pausing: Ref<Record<string, boolean>>
  load: ReturnType<typeof vi.fn>
  create: ReturnType<typeof vi.fn>
  remove: ReturnType<typeof vi.fn>
  pause: ReturnType<typeof vi.fn>
  resume: ReturnType<typeof vi.fn>
}

const { useSchedulesMock } = vi.hoisted(() => {
  const useSchedulesMockFn = vi.fn(() => {
    const harness = (globalThis as unknown as { __schedulesHarness?: SchedulesHarness })
      .__schedulesHarness
    if (!harness) throw new Error('schedules harness not installed yet')
    return harness
  })
  return { useSchedulesMock: useSchedulesMockFn }
})

const harness: SchedulesHarness = (() => {
  const vue = require('vue') as typeof import('vue')
  return {
    schedules: vue.ref<ScheduleSummary[]>([]),
    loading: vue.ref(false),
    error: vue.ref<string | null>(null),
    pausing: vue.ref<Record<string, boolean>>({}),
    load: vi.fn(),
    create: vi.fn(),
    remove: vi.fn(),
    pause: vi.fn(),
    resume: vi.fn(),
  }
})()

;(globalThis as unknown as { __schedulesHarness?: SchedulesHarness }).__schedulesHarness = harness

vi.mock('@cbs/admin-ui-plugin/composables/useSchedules', () => ({
  useSchedules: useSchedulesMock,
}))

// ---------------------------------------------------------------------------
// Probe stub for `DslScheduleList` — records props and re-emits events.
// ---------------------------------------------------------------------------

const DslScheduleListProbe = defineComponent({
  name: 'ScheduleList',
  props: ['schedules', 'loading', 'error', 'pausingDefinitions'],
  setup(props, { emit }) {
    return () =>
      h('div', { 'data-testid': 'dsl-schedule-list' }, [
        h(
          'button',
          {
            'data-testid': 'probe-create-button',
            onClick: () =>
              emit('create', {
                definition: 'weekly',
                cron: '0 0 * * 1',
                timezone: 'UTC',
              } as CreateSchedulePayload),
          },
          'Create',
        ),
        h(
          'button',
          {
            'data-testid': 'probe-delete-button',
            onClick: () => emit('delete', 'daily'),
          },
          'Delete',
        ),
        h(
          'button',
          {
            'data-testid': 'probe-pause-button',
            onClick: () => emit('pause', 'hourly'),
          },
          'Pause',
        ),
        h(
          'button',
          {
            'data-testid': 'probe-resume-button',
            onClick: () => emit('resume', 'hourly'),
          },
          'Resume',
        ),
        h('pre', { 'data-testid': 'probe-props' }, JSON.stringify(props)),
      ])
  },
})

const componentStubs = {
  ScheduleList: DslScheduleListProbe,
}

function mountPage() {
  return mount(SchedulesPage, {
    global: { stubs: componentStubs },
    attachTo: document.body,
  })
}

const flush = async () => {
  await flushPromises()
  await nextTick()
  await nextTick()
}

describe('schedules.vue page wiring', () => {
  beforeEach(() => {
    harness.schedules.value = []
    harness.loading.value = false
    harness.error.value = null
    harness.pausing.value = {}
    harness.load.mockClear()
    harness.create.mockClear()
    harness.remove.mockClear()
    harness.pause.mockClear()
    harness.resume.mockClear()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('calls load exactly once on mount', async () => {
    const wrapper = mountPage()
    await flush()

    expect(harness.load).toHaveBeenCalledTimes(1)

    wrapper.unmount()
  })

  it('reflects harness ref changes on the stub props', async () => {
    const wrapper = mountPage()
    await flush()

    harness.schedules.value = [
      {
        scheduleId: 's1',
        definition: 'hourly',
        cron: '0 * * * *',
        timezone: 'UTC',
        note: 'every hour',
        nextRunAt: '2026-01-01T01:00:00Z',
        paused: false,
      },
    ]
    harness.loading.value = true
    harness.error.value = 'Backend unreachable'
    harness.pausing.value = { hourly: true }
    await flush()

    const probe = wrapper.findComponent(DslScheduleListProbe)
    expect(probe.props('schedules')).toEqual([
      {
        scheduleId: 's1',
        definition: 'hourly',
        cron: '0 * * * *',
        timezone: 'UTC',
        note: 'every hour',
        nextRunAt: '2026-01-01T01:00:00Z',
        paused: false,
      },
    ])
    expect(probe.props('loading')).toBe(true)
    expect(probe.props('error')).toBe('Backend unreachable')
    expect(probe.props('pausingDefinitions')).toEqual({ hourly: true })

    wrapper.unmount()
  })

  it('forwards create event to the create spy with payload', async () => {
    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="probe-create-button"]').trigger('click')
    await flush()

    expect(harness.create).toHaveBeenCalledTimes(1)
    expect(harness.create).toHaveBeenCalledWith({
      definition: 'weekly',
      cron: '0 0 * * 1',
      timezone: 'UTC',
    })

    wrapper.unmount()
  })

  it('forwards delete event to the remove spy with id', async () => {
    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="probe-delete-button"]').trigger('click')
    await flush()

    expect(harness.remove).toHaveBeenCalledTimes(1)
    expect(harness.remove).toHaveBeenCalledWith('daily')

    wrapper.unmount()
  })

  it('forwards pause event to the pause spy with definition', async () => {
    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="probe-pause-button"]').trigger('click')
    await flush()

    expect(harness.pause).toHaveBeenCalledTimes(1)
    expect(harness.pause).toHaveBeenCalledWith('hourly')

    wrapper.unmount()
  })

  it('forwards resume event to the resume spy with definition', async () => {
    const wrapper = mountPage()
    await flush()

    await wrapper.find('[data-testid="probe-resume-button"]').trigger('click')
    await flush()

    expect(harness.resume).toHaveBeenCalledTimes(1)
    expect(harness.resume).toHaveBeenCalledWith('hourly')

    wrapper.unmount()
  })
})
