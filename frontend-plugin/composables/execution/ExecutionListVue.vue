<template>
  <div>
    <table class="execution-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>Workflow Code</th>
          <th>Status</th>
          <th>Created At</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="execution in executions"
          :key="execution.id"
          class="execution-row"
          @click="$emit('select', execution.id)"
        >
          <td>{{ execution.id }}</td>
          <td>{{ execution.workflowCode }}</td>
          <td>
            <span :class="['status-badge', `status-${execution.status.toLowerCase()}`]">
              {{ execution.status }}
            </span>
          </td>
          <td>{{ formatDate(execution.createdAt) }}</td>
        </tr>
      </tbody>
    </table>
    <div
      v-if="executions.length === 0"
      data-testid="empty-state"
      class="empty-state"
    >
      No workflow executions found.
    </div>
  </div>
</template>

<script lang="ts">
import type { PropType } from 'vue';
import type { WorkflowExecution } from './WorkflowExecution';

export default {
  name: 'ExecutionListVue',
  props: {
    executions: {
      type: Array as PropType<WorkflowExecution[]>,
      required: true,
    },
  },
  emits: ['select'],
  methods: {
    formatDate(dateStr: string): string {
      return new Date(dateStr).toLocaleString();
    },
  },
};
</script>

<style scoped>
.execution-table {
  width: 100%;
  border-collapse: collapse;
}

.execution-table thead th {
  text-align: left;
  padding: 0.75rem 1rem;
  border-bottom: 2px solid #e5e7eb;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #6b7280;
  background: #f9fafb;
}

.execution-row {
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.execution-row:hover {
  background-color: #f3f4f6;
}

.execution-row td {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e5e7eb;
  font-size: 0.875rem;
}

.status-badge {
  display: inline-block;
  padding: 0.25rem 0.5rem;
  border-radius: 0.25rem;
  font-size: 0.75rem;
  font-weight: 500;
}

.status-active {
  background-color: #dbeafe;
  color: #1d4ed8;
}

.status-closed {
  background-color: #d1fae5;
  color: #065f46;
}

.status-faulted {
  background-color: #fee2e2;
  color: #b91c1c;
}

.empty-state {
  padding: 2rem;
  text-align: center;
  color: #6b7280;
  font-size: 0.875rem;
}
</style>
