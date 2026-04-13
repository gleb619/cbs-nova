import { TokenStorage } from '@cbs/admin-plugin/composables/auth/TokenStorage';
import type { WorkflowExecution } from '@cbs/admin-plugin/composables/execution/WorkflowExecution';
import type { PageResult, WorkflowExecutionRepository } from '@cbs/admin-plugin/composables/execution/WorkflowExecutionRepository';
import type { AxiosHttp } from '@cbs/admin-plugin/composables/http/AxiosHttp';

export class WorkflowExecutionHttp implements WorkflowExecutionRepository {
  constructor(private readonly http: AxiosHttp) {}

  async findAll(page: number, size: number): Promise<PageResult<WorkflowExecution>> {
    const token = TokenStorage.get();
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const response = await this.http.get<PageResult<WorkflowExecution>>(`/api/executions?page=${page}&size=${size}`, { headers });
    return response.data;
  }

  async findById(id: number): Promise<WorkflowExecution> {
    const token = TokenStorage.get();
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const response = await this.http.get<WorkflowExecution>(`/api/executions/${id}`, { headers });
    return response.data;
  }
}
