import { inject, ref } from 'vue';
import { HTTP_KEY } from '../http/createHttp';
import type { WorkflowExecution } from './WorkflowExecution';
import { fetchExecution, fetchExecutions } from './execution.service';
import type { PageResult } from './execution.service';

export function useExecution() {
  const http = inject(HTTP_KEY)!;
  const executions = ref<WorkflowExecution[]>([]);
  const execution = ref<WorkflowExecution | null>(null);
  const loading = ref(false);
  const error = ref<string | null>(null);
  const currentPage = ref(0);
  const totalPages = ref(1);
  const totalElements = ref(0);
  const pageSize = 20;

  const loadPage = async (page: number): Promise<void> => {
    loading.value = true;
    error.value = null;
    try {
      const result: PageResult<WorkflowExecution> = await fetchExecutions(http, page, pageSize);
      executions.value = result.content;
      currentPage.value = result.number;
      totalPages.value = result.totalPages;
      totalElements.value = result.totalElements;
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load workflow executions';
    } finally {
      loading.value = false;
    }
  };

  const loadOne = async (id: number): Promise<void> => {
    loading.value = true;
    error.value = null;
    try {
      execution.value = await fetchExecution(http, id);
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load workflow execution';
    } finally {
      loading.value = false;
    }
  };

  return { executions, execution, loading, error, currentPage, totalPages, totalElements, loadPage, loadOne };
}