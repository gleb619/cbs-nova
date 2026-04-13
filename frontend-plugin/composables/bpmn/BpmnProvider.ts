import { key, piqureWrapper } from 'piqure';
import type { BpmnRepository } from './BpmnRepository';

const { provide, inject } = piqureWrapper(typeof window !== 'undefined' ? window : ({} as Window), 'piqure');

export const BPMN_REPOSITORY = key<BpmnRepository>('BpmnRepository');

export const provideForBpmn = (repository: BpmnRepository): void => {
  provide(BPMN_REPOSITORY, repository);
};

export { inject };
