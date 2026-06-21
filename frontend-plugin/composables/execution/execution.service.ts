import type { AxiosInstance } from 'axios';
import type { WorkflowExecution } from './WorkflowExecution';

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export async function fetchExecutions(
  http: AxiosInstance,
  page: number,
  size: number,
): Promise<PageResult<WorkflowExecution>> {
  const response = await http.get<PageResult<WorkflowExecution>>(`/api/executions?page=${page}&size=${size}`);
  return response.data;
}

export async function fetchExecution(http: AxiosInstance, id: number): Promise<WorkflowExecution> {
  const response = await http.get<WorkflowExecution>(`/api/executions/${id}`);
  return response.data;
}