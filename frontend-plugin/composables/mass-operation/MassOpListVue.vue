<template>
  <div>
    <table class="mass-op-table">
      <thead>
        <tr>
          <th>ID</th>
          <th>Code</th>
          <th>Status</th>
          <th>Total Items</th>
          <th>Processed</th>
          <th>Started At</th>
        </tr>
      </thead>
      <tbody>
        <tr
          v-for="op in sortedOps"
          :key="op.id"
          class="mass-op-row"
          @click="$emit('select', op.id)"
        >
          <td>{{ op.id }}</td>
          <td>{{ op.code }}</td>
          <td>
            <span :class="['status-badge', `status-${op.status.toLowerCase()}`]">
              {{ op.status }}
            </span>
          </td>
          <td>{{ op.totalItems }}</td>
          <td>{{ op.processedCount }}</td>
          <td>{{ formatDate(op.startedAt) }}</td>
        </tr>
      </tbody>
    </table>
    <div
      v-if="executions.length === 0"
      data-testid="empty-state"
      class="empty-state"
    >
      No mass operation executions found.
    </div>
  </div>
</template>

<script lang="ts">
import type { PropType } from 'vue';
import type { MassOperation } from './MassOperation';

export default {
  name: 'MassOpListVue',
  props: {
    executions: {
      type: Array as PropType<MassOperation[]>,
      required: true,
    },
  },
  emits: ['select'],
  computed: {
    sortedOps(): MassOperation[] {
      return [...this.executions].sort(
        (a, b) => new Date(b.startedAt).getTime() - new Date(a.startedAt).getTime(),
      );
    },
  },
  methods: {
    formatDate(dateStr: string): string {
      return new Date(dateStr).toLocaleString();
    },
  },
};
</script>

<style scoped>
.mass-op-table {
  width: 100%;
  border-collapse: collapse;
}

.mass-op-table thead th {
  text-align: left;
  padding: 0.75rem 1rem;
  border-bottom: 2px solid #e5e7eb;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #6b7280;
  background: #f9fafb;
}

.mass-op-row {
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.mass-op-row:hover {
  background-color: #f3f4f6;
}

.mass-op-row td {
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

.status-completed {
  background-color: #d1fae5;
  color: #065f46;
}

.status-running {
  background-color: #dbeafe;
  color: #1d4ed8;
}

.status-failed {
  background-color: #fee2e2;
  color: #b91c1c;
}

.status-pending {
  background-color: #fef3c7;
  color: #92400e;
}

.empty-state {
  padding: 2rem;
  text-align: center;
  color: #6b7280;
  font-size: 0.875rem;
}
</style>
