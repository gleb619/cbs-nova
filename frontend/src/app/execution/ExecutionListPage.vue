<template>
  <div class="execution-list-page">
    <h1 class="page-title">Workflow Executions</h1>

    <div v-if="loading" class="loading-indicator">
      Loading...
    </div>

    <div
      v-else-if="error"
      class="error-message"
    >
      {{ error }}
    </div>

    <ExecutionListVue
      v-else
      :executions="executions"
      @select="navigateToDetail"
    />

    <div
      v-if="!loading && !error && totalPages > 1"
      class="pagination"
    >
      <button
        :disabled="currentPage === 0"
        @click="loadPage(currentPage - 1)"
      >
        Previous
      </button>
      <span>Page {{ currentPage + 1 }} of {{ totalPages }}</span>
      <button
        :disabled="currentPage >= totalPages - 1"
        @click="loadPage(currentPage + 1)"
      >
        Next
      </button>
    </div>
  </div>
</template>

<script lang="ts">
import ExecutionListVue from '@cbs/admin-plugin/composables/execution/ExecutionListVue.vue';
import { useExecution } from '@cbs/admin-plugin/composables/execution/useExecution';
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'ExecutionListPageVue',
  components: { ExecutionListVue },
  setup() {
    const { executions, loading, error, currentPage, totalPages, loadPage } = useExecution();
    loadPage(0);
    return { executions, loading, error, currentPage, totalPages, loadPage };
  },
  methods: {
    navigateToDetail(id: number) {
      this.$router.push({ name: 'ExecutionDetail', params: { id: String(id) } });
    },
  },
});
</script>

<style scoped>
.execution-list-page {
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
  margin-bottom: 1rem;
}

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 1rem;
  padding: 1rem 0;
}

.pagination button {
  padding: 0.5rem 1rem;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  background: white;
  cursor: pointer;
  font-size: 0.875rem;
}

.pagination button:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.pagination button:hover:not(:disabled) {
  background-color: #f3f4f6;
}
</style>