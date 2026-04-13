<template>
  <div class="mass-op-detail-page">
    <h1 class="page-title">Mass Operation Detail</h1>

    <div v-if="loading" class="loading-indicator">
      Loading...
    </div>

    <div v-else-if="error" class="error-message">
      {{ error }}
    </div>

    <div v-else-if="execution" class="detail-content">
      <div class="execution-header">
        <div class="field-group">
          <label>ID</label>
          <span>{{ execution.id }}</span>
        </div>
        <div class="field-group">
          <label>Code</label>
          <span>{{ execution.code }}</span>
        </div>
        <div class="field-group">
          <label>Category</label>
          <span>{{ execution.category }}</span>
        </div>
        <div class="field-group">
          <label>DSL Version</label>
          <span>{{ execution.dslVersion }}</span>
        </div>
        <div class="field-group">
          <label>Status</label>
          <span :class="['status-badge', `status-${execution.status.toLowerCase()}`]">
            {{ execution.status }}
          </span>
        </div>
        <div class="field-group">
          <label>Total Items</label>
          <span>{{ execution.totalItems }}</span>
        </div>
        <div class="field-group">
          <label>Processed</label>
          <span>{{ execution.processedCount }}</span>
        </div>
        <div class="field-group">
          <label>Failed</label>
          <span>{{ execution.failedCount }}</span>
        </div>
        <div class="field-group">
          <label>Trigger Type</label>
          <span>{{ execution.triggerType }}</span>
        </div>
        <div class="field-group">
          <label>Trigger Source</label>
          <span>{{ execution.triggerSource }}</span>
        </div>
        <div class="field-group">
          <label>Performed By</label>
          <span>{{ execution.performedBy }}</span>
        </div>
        <div class="field-group">
          <label>Started At</label>
          <span>{{ formatDate(execution.startedAt) }}</span>
        </div>
        <div class="field-group">
          <label>Completed At</label>
          <span>{{ execution.completedAt ? formatDate(execution.completedAt) : '—' }}</span>
        </div>
        <div class="field-group">
          <label>Temporal Workflow ID</label>
          <span>{{ execution.temporalWorkflowId }}</span>
        </div>
      </div>

      <button class="retry-btn" @click="retryFailed">
        Retry Failed
      </button>

      <div v-if="retryResult" class="success-message">
        {{ retryResult }}
      </div>

      <h2 class="items-title">Items</h2>

      <div v-if="itemsLoading" class="loading-indicator">
        Loading items...
      </div>

      <div v-else-if="itemsError" class="error-message">
        {{ itemsError }}
      </div>

      <table v-else class="items-table">
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
              <span :class="['status-badge', `status-${item.status.toLowerCase()}`]">
                {{ item.status }}
              </span>
            </td>
            <td>{{ item.errorMessage || '—' }}</td>
            <td>{{ formatDate(item.startedAt) }}</td>
            <td>{{ item.completedAt ? formatDate(item.completedAt) : '—' }}</td>
          </tr>
        </tbody>
      </table>

      <div v-if="!itemsLoading && !itemsError && items.length === 0" class="empty-state">
        No items found for this mass operation.
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { MASS_OPERATION_REPOSITORY, inject } from '@cbs/admin-plugin/composables/mass-operation/MassOperationProvider';
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'MassOpDetailPageVue',
  data() {
    return {
      execution: null as any,
      items: [] as any[],
      loading: false,
      itemsLoading: false,
      error: null as string | null,
      itemsError: null as string | null,
      retryResult: null as string | null,
    };
  },
  async mounted() {
    await this.fetchExecution();
    await this.loadItems();
  },
  methods: {
    async fetchExecution() {
      this.loading = true;
      this.error = null;
      try {
        const id = Number(this.$route.params.id);
        const repository = inject(MASS_OPERATION_REPOSITORY);
        this.execution = await repository.findById(id);
      } catch (err: any) {
        this.error = err?.message || 'Failed to load mass operation';
      } finally {
        this.loading = false;
      }
    },
    async loadItems() {
      this.itemsLoading = true;
      this.itemsError = null;
      try {
        const id = Number(this.$route.params.id);
        const repository = inject(MASS_OPERATION_REPOSITORY);
        this.items = await repository.findItems(id);
      } catch (err: any) {
        this.itemsError = err?.message || 'Failed to load items';
      } finally {
        this.itemsLoading = false;
      }
    },
    async retryFailed() {
      this.retryResult = null;
      try {
        const id = Number(this.$route.params.id);
        const repository = inject(MASS_OPERATION_REPOSITORY);
        const result = await repository.retryFailed(id);
        this.retryResult = `Retried ${result.retriedCount} items`;
        await this.loadItems();
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
