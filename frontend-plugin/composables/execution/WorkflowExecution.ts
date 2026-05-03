export interface WorkflowExecution {
  id: number;
  workflowCode: string;
  dslVersion: string;
  currentState: string;
  status: string;
  context: string;
  displayData: string;
  performedBy: string;
  createdAt: string;
  updatedAt: string;
}
