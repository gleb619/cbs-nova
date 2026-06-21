import type { AuthenticatedUser } from './AuthenticatedUser';

export type Credentials = { username: string; password: string };

export interface AuthLike {
  currentUser(): Promise<AuthenticatedUser>;
  login(credentials?: Credentials): Promise<void>;
  logout(): Promise<void>;
  authenticated(): Promise<boolean>;
  refreshToken(): Promise<string>;
}

let _auth: AuthLike | null = null;

export const setAuth = (repo: AuthLike | null): void => {
  _auth = repo;
};

export const getAuth = (): AuthLike => {
  if (!_auth) throw new Error('Auth not initialized');
  return _auth;
};