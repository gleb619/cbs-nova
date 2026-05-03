import { piqureWrapper } from 'piqure';
import { key } from 'piqure';
import type { AuthRepository } from './AuthRepository';

const { provide, inject } = piqureWrapper(typeof window !== 'undefined' ? window : ({} as Window), 'piqure');

export const AUTH_REPOSITORY = key<AuthRepository>('AuthRepository');

export const provideForAuth = (repository: AuthRepository): void => {
  provide(AUTH_REPOSITORY, repository);
};

export { inject };
