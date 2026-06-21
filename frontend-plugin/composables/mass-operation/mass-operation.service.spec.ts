import type { AxiosInstance } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import type { MassOperation } from './MassOperation';
import {
  fetchMassOperation,
  fetchMassOperations,
  fetchMassOperationItems,
  triggerMassOperation,
  retryFailedMassOperation,
} from './mass-operation.service';
import type { TriggerRequest, RetryResult } from './mass-operation.service';

const stubMassOp = (): MassOperation => ({
  id: 1,
  code: 'BATCH_001',
  category: 'CREDIT',
  dslVersion: '1.0.0',
  status: 'COMPLETED',
  totalItems: 10,
  processedCount: 10,
  failedCount: 0,
  triggerType: 'MANUAL',
  triggerSource: 'UI',
  performedBy: 'admin1',
  startedAt: '2026-01-01T00:00:00Z',
  completedAt: '2026-01-01T01:00:00Z',
  temporalWorkflowId: 'wf-abc',
});

const mockHttp = (data: unknown, method: 'get' | 'post' = 'get'): AxiosInstance =>
  ({ [method]: vi.fn().mockResolvedValue({ data }) } as unknown as AxiosInstance);

describe('mass-operation.service', () => {
  it('should fetch all mass operations from /api/mass-operations', async () => {
    const http = mockHttp([stubMassOp()]);
    const result = await fetchMassOperations(http);
    expect(http.get).toHaveBeenCalledWith('/api/mass-operations');
    expect(result).toEqual([stubMassOp()]);
  });

  it('should fetch single mass operation from /api/mass-operations/:id', async () => {
    const http = mockHttp(stubMassOp());
    const result = await fetchMassOperation(http, 1);
    expect(http.get).toHaveBeenCalledWith('/api/mass-operations/1');
    expect(result).toEqual(stubMassOp());
  });

  it('should fetch mass operation items from /api/mass-operations/:id/items', async () => {
    const http = mockHttp([]);
    await fetchMassOperationItems(http, 1);
    expect(http.get).toHaveBeenCalledWith('/api/mass-operations/1/items');
  });

  it('should trigger mass operation via POST /api/mass-operations/trigger', async () => {
    const http = mockHttp(stubMassOp(), 'post');
    const req: TriggerRequest = { massOpCode: 'BATCH_001', performedBy: 'admin1', dslVersion: '1.0.0' };
    await triggerMassOperation(http, req);
    expect(http.post).toHaveBeenCalledWith('/api/mass-operations/trigger', req);
  });

  it('should retry failed items via POST /api/mass-operations/:id/retry', async () => {
    const http = mockHttp({ retriedCount: 3 } as RetryResult, 'post');
    const result = await retryFailedMassOperation(http, 1);
    expect(http.post).toHaveBeenCalledWith('/api/mass-operations/1/retry', undefined);
    expect(result.retriedCount).toBe(3);
  });
});