import type { AuthenticatedUser } from './AuthenticatedUser';

export type Credentials = { username: string; password: string };

export interface AuthRepository {
  currentUser(): Promise<AuthenticatedUser>;
  login(credentials?: Credentials): Promise<void>;
  logout(): Promise<void>;
  authenticated(): Promise<boolean>;
  refreshToken(): Promise<string>;
}
