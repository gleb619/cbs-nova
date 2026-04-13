import { key, piqureWrapper } from 'piqure';
import type { WorkflowExecutionRepository } from './WorkflowExecutionRepository';

const { provide, inject } = piqureWrapper(typeof window !== 'undefined' ? window : ({} as Window), 'piqure');

export const EXECUTION_REPOSITORY = key<WorkflowExecutionRepository>('WorkflowExecutionRepository');

export const provideForExecution = (repository: WorkflowExecutionRepository): void => {
  provide(EXECUTION_REPOSITORY, repository);
};

export { inject };
