import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import type { ObjectStructureDto } from '../../types/dsl'
import StructureFieldsTable from '../dsl/StructureFieldsTable.vue'

const structure: ObjectStructureDto = {
  name: 'BatchProcessing',
  type: 'process',
  logic: [
    {
      kind: 'execute',
      status: 'configured',
      required: true,
      description: 'User-defined execute logic; required for the object to run',
    },
    {
      kind: 'preview',
      status: 'default',
      required: false,
      description: 'No preview logic configured; falls back to execute logic',
    },
    {
      kind: 'explain',
      status: 'configured',
      required: false,
      description: 'User-defined explain logic',
    },
  ],
  fields: [
    {
      path: 'taskQueue',
      value: 'BatchProcessing-queue',
      type: 'java.lang.String',
      description: 'Temporal task queue this workflow polls',
    },
    {
      path: 'timeout',
      type: 'java.time.Duration',
    },
    {
      path: 'signals',
      value: '2',
      type: 'java.util.List',
      description: 'Declared signals',
    },
    {
      path: 'signals[0].name',
      value: 'submit',
      type: 'java.lang.String',
    },
    {
      path: 'signals[1].name',
      value: 'cancel',
      type: 'java.lang.String',
    },
  ],
}

describe('StructureFieldsTable', () => {
  it('exposes root data-testid', () => {
    const wrapper = mount(StructureFieldsTable, { props: { structure } })

    expect(wrapper.find('[data-testid="structure-fields"]').exists()).toBe(true)
  })

  it('renders the header with object name and uppercase type badge', () => {
    const wrapper = mount(StructureFieldsTable, { props: { structure } })

    const header = wrapper.find('[data-testid="structure-fields-header"]')
    expect(header.exists()).toBe(true)
    expect(header.text()).toContain('BatchProcessing')
    expect(header.text()).toContain('process')
  })

  it('renders a row per field with path, value, type and description', () => {
    const wrapper = mount(StructureFieldsTable, { props: { structure } })

    const rows = wrapper.findAll('tbody tr')
    expect(rows).toHaveLength(5)

    const first = wrapper.find('[data-testid="structure-field-taskQueue"]')
    expect(first.exists()).toBe(true)
    expect(first.text()).toContain('taskQueue')
    expect(first.text()).toContain('BatchProcessing-queue')
    expect(first.text()).toContain('java.lang.String')
    expect(first.text()).toContain('Temporal task queue this workflow polls')
  })

  it('renders an em dash when the value is missing', () => {
    const wrapper = mount(StructureFieldsTable, { props: { structure } })

    const row = wrapper.find('[data-testid="structure-field-timeout"]')
    expect(row.exists()).toBe(true)
    expect(row.text()).toContain('—')
  })

  it('sanitizes brackets in per-row data-testids and indents array-element paths', () => {
    const wrapper = mount(StructureFieldsTable, { props: { structure } })

    const elementRow = wrapper.find('[data-testid="structure-field-signals-0.name"]')
    expect(elementRow.exists()).toBe(true)
    expect(elementRow.text()).toContain('signals[0].name')
    expect(elementRow.find('td').classes()).toContain('pl-6')

    const parentRow = wrapper.find('[data-testid="structure-field-signals"]')
    expect(parentRow.exists()).toBe(true)
    expect(parentRow.find('td').classes()).not.toContain('pl-6')
  })

  it('renders the empty state when the object has no fields', () => {
    const empty: ObjectStructureDto = {
      name: 'EmptyHelper',
      type: 'helper',
      logic: [],
      fields: [],
    }
    const wrapper = mount(StructureFieldsTable, { props: { structure: empty } })

    expect(wrapper.text()).toContain('No fields.')
    expect(wrapper.find('[data-testid="structure-fields-table"]').exists()).toBe(false)
  })

  it('renders fields when the backend omits the logic key (pre-introspection payload)', () => {
    const legacy = {
      name: 'BatchProcessing',
      type: 'process',
      fields: [
        {
          path: 'taskQueue',
          value: 'BatchProcessing-queue',
          type: 'java.lang.String',
        },
      ],
    } as ObjectStructureDto
    const wrapper = mount(StructureFieldsTable, { props: { structure: legacy } })

    expect(wrapper.find('[data-testid="structure-logic"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="structure-fields-table"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('BatchProcessing-queue')
  })

  it('renders a logic token per kind with status text', () => {
    const wrapper = mount(StructureFieldsTable, { props: { structure } })

    const logic = wrapper.find('[data-testid="structure-logic"]')
    expect(logic.exists()).toBe(true)

    const execute = wrapper.find('[data-testid="structure-logic-execute"]')
    expect(execute.exists()).toBe(true)
    expect(execute.text()).toContain('execute: configured')

    const preview = wrapper.find('[data-testid="structure-logic-preview"]')
    expect(preview.exists()).toBe(true)
    expect(preview.text()).toContain('preview: default')

    const explain = wrapper.find('[data-testid="structure-logic-explain"]')
    expect(explain.exists()).toBe(true)
    expect(explain.text()).toContain('explain: configured')
  })

  it('styles configured logic with the success token and default logic with the gray token', () => {
    const wrapper = mount(StructureFieldsTable, { props: { structure } })

    expect(wrapper.find('[data-testid="structure-logic-execute"]').classes()).toContain(
      'bg-success-100',
    )
    expect(wrapper.find('[data-testid="structure-logic-preview"]').classes()).toContain(
      'bg-gray-50',
    )
  })

  it('flags missing required logic with the error token', () => {
    const missingExecute: ObjectStructureDto = {
      ...structure,
      logic: [{ kind: 'execute', status: 'default', required: true }],
    }
    const wrapper = mount(StructureFieldsTable, { props: { structure: missingExecute } })

    const execute = wrapper.find('[data-testid="structure-logic-execute"]')
    expect(execute.classes()).toContain('bg-error-100')
    expect(execute.text()).toContain('required')
  })
})
