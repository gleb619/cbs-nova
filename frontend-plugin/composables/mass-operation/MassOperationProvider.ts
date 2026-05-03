import { key, piqureWrapper } from 'piqure';
import type { MassOperationRepository } from './MassOperationRepository';

const { provide, inject } = piqureWrapper(typeof window !== 'undefined' ? window : ({} as Window), 'piqure');

export const MASS_OPERATION_REPOSITORY = key<MassOperationRepository>('MassOperationRepository');

export const provideForMassOperation = (repository: MassOperationRepository): void => {
  provide(MASS_OPERATION_REPOSITORY, repository);
};

export { inject };
