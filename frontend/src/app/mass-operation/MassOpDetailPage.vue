<template>
  <div class="mass-op-detail-page">
    <h1 class="page-title">Mass Operation Detail</h1>
    <div
      v-if="loading"
      class="loading-indicator"
    >
      Loading...
    </div>
    <div
      v-else-if="error"
      class="error-message"
    >
      {{ error }}
    </div>
    <div
      v-else-if="massOperation"
      class="detail-content"
    >
      <div class="execution-header">
        <div class="field-group">
          <label>ID</label><span>{{ massOperation.id }}</span>
        </div>
        <div class="field-group">
          <label>Code</label><span>{{ massOperation.code }}</span>
        </div>
        <div class="field-group">
          <label>Category</label><span>{{ massOperation.category }}</span>
        </div>
        <div class="field-group">
          <label>DSL Version</label><span>{{ massOperation.dslVersion }}</span>
        </div>
        <div class="field-group">
          <label>Status</label>
          <span :class="['status-badge', `status-${massOperation.status.toLowerCase()}`]">{{ massOperation.status }}</span>
        </div>
        <div class="field-group">
          <label>Total Items</label><span>{{ massOperation.totalItems }}</span>
        </div>
        <div class="field-group">
          <label>Processed</label><span>{{ massOperation.processedCount }}</span>
        </div>
        <div class="field-group">
          <label>Failed</label><span>{{ massOperation.failedCount }}</span>
        </div>
        <div class="field-group">
          <label>Trigger Type</label><span>{{ massOperation.triggerType }}</span>
        </div>
        <div class="field-group">
          <label>Trigger Source</label><span>{{ massOperation.triggerSource }}</span>
        </div>
        <div class="field-group">
          <label>Performed By</label><span>{{ massOperation.performedBy }}</span>
        </div>
        <div class="field-group">
          <label>Started At</label><span>{{ formatDate(massOperation.startedAt) }}</span>
        </div>
        <div class="field-group">
          <label>Completed At</label><span>{{ massOperation.completedAt ? formatDate(massOperation.completedAt) : '—' }}</span>
        </div>
        <div class="field-group">
          <label>Temporal Workflow ID</label><span>{{ massOperation.temporalWorkflowId }}</span>
        </div>
      </div>
      <button
        class="retry-btn"
        @click="handleRetry"
      >
        Retry Failed
      </button>
      <div
        v-if="retryMsg"
        class="success-message"
      >
        {{ retryMsg }}
      </div>
      <h2 class="items-title">Items</h2>
      <div
        v-if="itemsLoading"
        class="loading-indicator"
      >
        Loading items...
      </div>
      <div
        v-else-if="itemsError"
        class="error-message"
      >
        {{ itemsError }}
      </div>
      <table
        v-else
        class="items-table"
      >
        <thead>
          <tr>
            <th>ID</th>
            <th>Item Key</th>
            <th>Status</th>
            <th>Error Message</th>
            <th>Started At</th>
            <th>Completed At</th>
          </tr>
        </thead>
        <tbody>
          <tr
            v-for="item in items"
            :key="item.id"
          >
            <td>{{ item.id }}</td>
            <td>{{ item.itemKey }}</td>
            <td>
              <span :class="['status-badge', `status-${item.status.toLowerCase()}`]">{{ item.status }}</span>
            </td>
            <td>{{ item.errorMessage || '—' }}</td>
            <td>{{ formatDate(item.startedAt) }}</td>
            <td>{{ item.completedAt ? formatDate(item.completedAt) : '—' }}</td>
          </tr>
        </tbody>
      </table>
      <div
        v-if="!itemsLoading && !itemsError && items.length === 0"
        class="empty-state"
      >
        No items found for this mass operation.
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { useMassOperation } from '@cbs/admin-plugin/composables/mass-operation/useMassOperation';
import { defineComponent, ref } from 'vue';

export default defineComponent({
  name: 'MassOpDetailPageVue',
  setup() {
    const { massOperation, items, loading, itemsLoading, error, itemsError, loadOne, loadItems, retryFailed } = useMassOperation();
    const retryMsg = ref<string | null>(null);
    return {
      massOperation,
      items,
      loading,
      itemsLoading,
      error,
      itemsError,
      loadOne,
      loadItems,
      retryFailed,
      retryMsg,
    };
  },
  async mounted() {
    const id = Number(this.$route.params.id);
    await this.loadOne(id);
    await this.loadItems(id);
  },
  methods: {
    async handleRetry() {
      this.retryMsg = null;
      try {
        const id = Number(this.$route.params.id);
        const result = await this.retryFailed(id);
        this.retryMsg = `Retried ${result.retriedCount} items`;
        await this.loadItems(id);
      } catch {
        this.itemsError = 'Retry failed';
      }
    },
    formatDate(dateStr: string): string {
      return new Date(dateStr).toLocaleString();
    },
  },
});
</script>

<style scoped>
.mass-op-detail-page {
  padding: 1.5rem;
}

.page-title {
  font-size: 1.25rem;
  font-weight: 600;
  color: #111827;
  margin-bottom: 1rem;
}

.loading-indicator {
  padding: 2rem;
  text-align: center;
  color: #6b7280;
}

.error-message {
  padding: 1rem;
  background-color: #fee2e2;
  color: #b91c1c;
  border-radius: 0.375rem;
}

.success-message {
  padding: 1rem;
  background-color: #d1fae5;
  color: #065f46;
  border-radius: 0.375rem;
  margin-bottom: 1rem;
}

.detail-content {
  display: flex;
  flex-direction: column;
  gap: 1rem;
}

.execution-header {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 1rem;
}

.field-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.field-group label {
  font-size: 0.75rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #6b7280;
}

.field-group span {
  font-size: 0.875rem;
  color: #111827;
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

.retry-btn {
  padding: 0.5rem 1rem;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  background: white;
  cursor: pointer;
  font-size: 0.875rem;
  align-self: flex-start;
}

.retry-btn:hover {
  background-color: #f3f4f6;
}

.items-title {
  font-size: 1rem;
  font-weight: 600;
  color: #374151;
  margin-top: 0.5rem;
}

.items-table {
  width: 100%;
  border-collapse: collapse;
}

.items-table thead th {
  text-align: left;
  padding: 0.75rem 1rem;
  border-bottom: 2px solid #e5e7eb;
  font-size: 0.75rem;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  color: #6b7280;
  background: #f9fafb;
}

.items-table tbody td {
  padding: 0.75rem 1rem;
  border-bottom: 1px solid #e5e7eb;
  font-size: 0.875rem;
}

.empty-state {
  padding: 2rem;
  text-align: center;
  color: #6b7280;
  font-size: 0.875rem;
}
</style>