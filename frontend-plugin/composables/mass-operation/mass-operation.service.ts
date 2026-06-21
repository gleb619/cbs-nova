import type { AxiosInstance } from 'axios';
import type { MassOperation } from './MassOperation';
import type { MassOperationItem } from './MassOperationItem';

export interface TriggerRequest {
  massOpCode: string;
  performedBy: string;
  dslVersion: string;
  contextJson?: string;
  triggerType?: string;
  triggerSource?: string;
}

export interface RetryResult {
  retriedCount: number;
}

export async function fetchMassOperations(http: AxiosInstance): Promise<MassOperation[]> {
  const response = await http.get<MassOperation[]>('/api/mass-operations');
  return response.data;
}

export async function fetchMassOperation(http: AxiosInstance, id: number): Promise<MassOperation> {
  const response = await http.get<MassOperation>(`/api/mass-operations/${id}`);
  return response.data;
}

export async function fetchMassOperationItems(http: AxiosInstance, id: number): Promise<MassOperationItem[]> {
  const response = await http.get<MassOperationItem[]>(`/api/mass-operations/${id}/items`);
  return response.data;
}

export async function triggerMassOperation(http: AxiosInstance, request: TriggerRequest): Promise<MassOperation> {
  const response = await http.post<MassOperation>('/api/mass-operations/trigger', request);
  return response.data;
}

export async function retryFailedMassOperation(http: AxiosInstance, id: number): Promise<RetryResult> {
  const response = await http.post<RetryResult>(`/api/mass-operations/${id}/retry`, undefined);
  return response.data;
}