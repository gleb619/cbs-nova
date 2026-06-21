import { getAuth } from './auth.store';

export const useAuth = () => {
  const auth = getAuth();
  return {
    currentUser: () => auth.currentUser(),
    login: (credentials?: { username: string; password: string }) => auth.login(credentials),
    logout: () => auth.logout(),
    authenticated: () => auth.authenticated(),
    refreshToken: () => auth.refreshToken(),
  };
};