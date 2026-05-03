export interface MassOperation {
  id: number;
  code: string;
  category: string;
  dslVersion: string;
  status: string;
  totalItems: number;
  processedCount: number;
  failedCount: number;
  triggerType: string;
  triggerSource: string;
  performedBy: string;
  startedAt: string;
  completedAt: string | null;
  temporalWorkflowId: string;
}
