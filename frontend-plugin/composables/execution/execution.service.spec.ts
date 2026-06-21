import type { AxiosInstance } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import type { WorkflowExecution } from './WorkflowExecution';
import { fetchExecution, fetchExecutions } from './execution.service';
import type { PageResult } from './execution.service';

const mockHttp = (data: unknown): AxiosInstance =>
  ({ get: vi.fn().mockResolvedValue({ data }) } as unknown as AxiosInstance);

const stubExecution = (): WorkflowExecution => ({
  id: 1,
  workflowCode: 'wf-001',
  dslVersion: '1.0.0',
  currentState: 'ACTIVE',
  status: 'ACTIVE',
  context: '{}',
  displayData: '{}',
  performedBy: 'user1',
  createdAt: '2026-01-01T00:00:00Z',
  updatedAt: '2026-01-01T00:00:00Z',
});

describe('execution.service', () => {
  it('should fetch paginated executions from /api/executions', async () => {
    const page: PageResult<WorkflowExecution> = {
      content: [stubExecution()],
      totalElements: 1,
      totalPages: 1,
      number: 0,
      size: 20,
    };
    const http = mockHttp(page);

    const result = await fetchExecutions(http, 0, 20);

    expect(http.get).toHaveBeenCalledWith('/api/executions?page=0&size=20');
    expect(result).toEqual(page);
  });

  it('should fetch single execution from /api/executions/:id', async () => {
    const execution = stubExecution();
    const http = mockHttp(execution);

    const result = await fetchExecution(http, 1);

    expect(http.get).toHaveBeenCalledWith('/api/executions/1');
    expect(result).toEqual(execution);
  });
});