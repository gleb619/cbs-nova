import type { WorkflowExecution } from './WorkflowExecution';

export interface PageResult<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export interface WorkflowExecutionRepository {
  findAll(page: number, size: number): Promise<PageResult<WorkflowExecution>>;
  findById(id: number): Promise<WorkflowExecution>;
}
