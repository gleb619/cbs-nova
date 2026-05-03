<template>
  <div class="mass-op-list-page">
    <h1 class="page-title">Mass Operations</h1>

    <div v-if="loading" class="loading-indicator">
      Loading...
    </div>

    <div v-else-if="error" class="error-message">
      {{ error }}
    </div>

    <div v-else>
      <button class="trigger-btn" @click="showTriggerForm = !showTriggerForm">
        {{ showTriggerForm ? 'Cancel' : 'Trigger' }}
      </button>

      <div v-if="showTriggerForm" class="trigger-form">
        <div class="form-group">
          <label for="massOpCode">Mass Op Code</label>
          <input
            id="massOpCode"
            v-model="triggerForm.massOpCode"
            type="text"
            required
          >
        </div>
        <div class="form-group">
          <label for="performedBy">Performed By</label>
          <input
            id="performedBy"
            v-model="triggerForm.performedBy"
            type="text"
            required
          >
        </div>
        <div class="form-group">
          <label for="dslVersion">DSL Version</label>
          <input
            id="dslVersion"
            v-model="triggerForm.dslVersion"
            type="text"
            required
          >
        </div>
        <button class="submit-btn" @click="submitTrigger">
          Submit
        </button>
        <div v-if="triggerError" class="error-message">
          {{ triggerError }}
        </div>
      </div>

      <MassOpListVue
        :executions="executions"
        @select="navigateToDetail"
      />
    </div>
  </div>
</template>

<script lang="ts">
import MassOpListVue from '@cbs/admin-plugin/composables/mass-operation/MassOpListVue.vue';
import { MASS_OPERATION_REPOSITORY, inject } from '@cbs/admin-plugin/composables/mass-operation/MassOperationProvider';
import { defineComponent } from 'vue';

export default defineComponent({
  name: 'MassOpListPageVue',
  components: {
    MassOpListVue,
  },
  data() {
    return {
      executions: [] as any[],
      loading: false,
      error: null as string | null,
      showTriggerForm: false,
      triggerForm: {
        massOpCode: '',
        performedBy: '',
        dslVersion: '',
      },
      triggerError: null as string | null,
    };
  },
  async mounted() {
    await this.loadExecutions();
  },
  methods: {
    async loadExecutions() {
      this.loading = true;
      this.error = null;
      try {
        const repository = inject(MASS_OPERATION_REPOSITORY);
        this.executions = await repository.findAll();
      } catch (err: any) {
        this.error = err?.message || 'Failed to load mass operations';
      } finally {
        this.loading = false;
      }
    },
    navigateToDetail(id: number) {
      this.$router.push({ name: 'MassOpDetail', params: { id: String(id) } });
    },
    async submitTrigger() {
      this.triggerError = null;
      try {
        const repository = inject(MASS_OPERATION_REPOSITORY);
        await repository.trigger({
          massOpCode: this.triggerForm.massOpCode,
          performedBy: this.triggerForm.performedBy,
          dslVersion: this.triggerForm.dslVersion,
        });
        this.showTriggerForm = false;
        this.triggerForm = { massOpCode: '', performedBy: '', dslVersion: '' };
        await this.loadExecutions();
      } catch (err: any) {
        this.triggerError = err?.message || 'Failed to trigger mass operation';
      }
    },
  },
});
</script>

<style scoped>
.mass-op-list-page {
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

.trigger-btn {
  padding: 0.5rem 1rem;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  background: white;
  cursor: pointer;
  font-size: 0.875rem;
  margin-bottom: 1rem;
}

.trigger-btn:hover {
  background-color: #f3f4f6;
}

.trigger-form {
  display: flex;
  flex-wrap: wrap;
  gap: 1rem;
  align-items: flex-end;
  margin-bottom: 1rem;
  padding: 1rem;
  background-color: #f9fafb;
  border-radius: 0.375rem;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 0.25rem;
}

.form-group label {
  font-size: 0.75rem;
  font-weight: 600;
  color: #6b7280;
}

.form-group input {
  padding: 0.5rem;
  border: 1px solid #d1d5db;
  border-radius: 0.375rem;
  font-size: 0.875rem;
}

.submit-btn {
  padding: 0.5rem 1rem;
  border: none;
  border-radius: 0.375rem;
  background-color: #d4532d;
  color: white;
  cursor: pointer;
  font-size: 0.875rem;
}

.submit-btn:hover {
  background-color: #b94424;
}
</style>
