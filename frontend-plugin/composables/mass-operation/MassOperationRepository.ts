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

export interface MassOperationRepository {
  findAll(): Promise<MassOperation[]>;
  findById(id: number): Promise<MassOperation>;
  findItems(id: number): Promise<MassOperationItem[]>;
  trigger(request: TriggerRequest): Promise<MassOperation>;
  retryFailed(id: number): Promise<RetryResult>;
}
