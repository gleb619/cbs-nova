import { AUTH_REPOSITORY, inject } from './AuthProvider';
import type { Credentials } from './AuthRepository';

export const useAuth = () => {
  const repo = inject(AUTH_REPOSITORY);
  return {
    currentUser: () => repo.currentUser(),
    login: (credentials?: Credentials) => repo.login(credentials),
    logout: () => repo.logout(),
    authenticated: () => repo.authenticated(),
    refreshToken: () => repo.refreshToken(),
  };
};
