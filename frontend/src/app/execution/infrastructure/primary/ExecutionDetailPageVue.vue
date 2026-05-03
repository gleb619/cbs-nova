<template>
  <div class="execution-detail-page">
    <h1 class="page-title">Workflow Execution Detail</h1>

    <div v-if="loading" class="loading-indicator">
      Loading...
    </div>

    <div v-else-if="error" class="error-message">
      {{ error }}
    </div>

    <div v-else-if="execution" class="detail-content">
      <div class="field-group">
        <label>ID</label>
        <span>{{ execution.id }}</span>
      </div>
      <div class="field-group">
        <label>Workflow Code</label>
        <span>{{ execution.workflowCode }}</span>
      </div>
      <div class="field-group">
        <label>DSL Version</label>
        <span>{{ execution.dslVersion }}</span>
      </div>
      <div class="field-group">
        <label>Current State</label>
        <span>{{ execution.currentState }}</span>
      </div>
      <div class="field-group">
        <label>Status</label>
        <span :class="['status-badge', `status-${execution.status.toLowerCase()}`]">
          {{ execution.status }}
        </span>
      </div>
      <div class="field-group">
        <label>Performed By</label>
        <span>{{ execution.performedBy }}</span>
      </div>
      <div class="field-group">
        <label>Created At</label>
        <span>{{ formatDate(execution.createdAt) }}</span>
      </div>
      <div class="field-group">
        <label>Updated At</label>
        <span>{{ formatDate(execution.updatedAt) }}</span>
      </div>
      <div class="field-group">
        <label>Context</label>
        <pre>{{ prettyJson(execution.context) }}</pre>
      </div>
      <div class="field-group">
        <label>Display Data</label>
        <pre>{{ prettyJson(execution.displayData) }}</pre>
      </div>

      <div class="field-group">
        <button class="bpmn-toggle-btn" @click="toggleBpmn">
          {{ bpmnOpen ? 'Hide Diagram' : 'Show Diagram' }}
        </button>
        <div v-if="bpmnOpen">
          <div v-if="bpmnLoading" class="loading-indicator">Loading diagram…</div>
          <div v-else-if="bpmnNotFound" class="no-diagram-message">No diagram available</div>
          <div v-else-if="bpmnError" class="error-message">{{ bpmnError }}</div>
          <BpmnViewer v-else-if="bpmnXml" :xml="bpmnXml" />
        </div>
      </div>
    </div>
  </div>
</template>

<script lang="ts">
import { BPMN_REPOSITORY, inject as injectBpmn } from '@cbs/admin-plugin/composables/bpmn/BpmnProvider';
import BpmnViewer from '@cbs/admin-plugin/composables/bpmn/BpmnViewer.vue';
import { EXECUTION_REPOSITORY, inject as injectExecution } from '@cbs/admin-plugin/composables/execution/WorkflowExecutionProvider';
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'ExecutionDetailPageVue',
  components: { BpmnViewer },
  data() {
    return {
      execution: null as any,
      loading: false,
      error: null as string | null,
      bpmnOpen: false,
      bpmnXml: null as string | null,
      bpmnLoading: false,
      bpmnError: null as string | null,
      bpmnNotFound: false,
    };
  },
  async mounted() {
    await this.fetchExecution();
  },
  methods: {
    async fetchExecution() {
      this.loading = true;
      this.error = null;
      try {
        const id = Number(this.$route.params.id);
        const repository = injectExecution(EXECUTION_REPOSITORY);
        this.execution = await repository.findById(id);
      } catch (err: any) {
        this.error = err?.message || 'Failed to load workflow execution';
      } finally {
        this.loading = false;
      }
    },
    async toggleBpmn() {
      this.bpmnOpen = !this.bpmnOpen;
      if (this.bpmnOpen && this.bpmnXml === null && !this.bpmnNotFound) {
        await this.fetchBpmn();
      }
    },
    async fetchBpmn() {
      this.bpmnLoading = true;
      this.bpmnError = null;
      try {
        const repository = injectBpmn(BPMN_REPOSITORY);
        this.bpmnXml = await repository.fetchXml(this.execution.workflowCode);
      } catch (err: any) {
        if (err?.response?.status === 404) {
          this.bpmnNotFound = true;
        } else {
          this.bpmnError = 'Failed to load diagram';
        }
      } finally {
        this.bpmnLoading = false;
      }
    },
    formatDate(dateStr: string): string {
      return new Date(dateStr).toLocaleString();
    },
    prettyJson(raw: string): string {
      try {
        return JSON.stringify(JSON.parse(raw), null, 2);
      } catch {
        return raw;
      }
    },
  },
});
</script>

<style scoped>
.execution-detail-page {
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

.detail-content {
  display: flex;
  flex-direction: column;
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

pre {
  background-color: #f9fafb;
  border: 1px solid #e5e7eb;
  border-radius: 0.375rem;
  padding: 1rem;
  font-size: 0.8rem;
  overflow-x: auto;
  white-space: pre-wrap;
  word-wrap: break-word;
}

.bpmn-toggle-btn {
  padding: 0.5rem 1rem;
  font-size: 0.875rem;
  font-weight: 500;
  color: #111827;
  background-color: #f3f4f6;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  cursor: pointer;
  transition: background-color 0.15s ease;
}

.bpmn-toggle-btn:hover {
  background-color: #e5e7eb;
}

.no-diagram-message {
  padding: 1rem;
  text-align: center;
  color: #6b7280;
  font-style: italic;
}
</style>
