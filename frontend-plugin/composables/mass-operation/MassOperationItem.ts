export interface MassOperationItem {
  id: number;
  itemKey: string;
  status: string;
  errorMessage: string | null;
  startedAt: string;
  completedAt: string | null;
}
