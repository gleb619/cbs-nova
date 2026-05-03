import type { LocalAuthHttp } from '@/auth/infrastructure/secondary/LocalAuthHttp';
import type { AuthRepository, Credentials } from '@cbs/admin-plugin/composables/auth/AuthRepository';
import type { AuthenticatedUser } from '@cbs/admin-plugin/composables/auth/AuthenticatedUser';
import { TokenStorage } from '@cbs/admin-plugin/composables/auth/TokenStorage';

export class LocalAuthRepository implements AuthRepository {
  constructor(private readonly localAuthHttp: LocalAuthHttp) {}

  async login(credentials?: Credentials): Promise<void> {
    if (!credentials) {
      throw new Error('credentials_required');
    }
    try {
      const token = await this.localAuthHttp.login(credentials.username, credentials.password);
      TokenStorage.set(token);
    } catch (error: unknown) {
      if (error instanceof Error && 'response' in error && (error as any).response?.status === 401) {
        throw new Error('invalid_credentials');
      }
      throw new Error('login_failed');
    }
  }

  async logout(): Promise<void> {
    TokenStorage.clear();
  }

  async currentUser(): Promise<AuthenticatedUser> {
    const token = TokenStorage.get();
    if (!token) {
      return { isAuthenticated: false, username: '', token: '' };
    }
    try {
      const payload = this.decodeJwt(token);
      const username = (payload.preferred_username as string) ?? '';
      return { isAuthenticated: true, username, token };
    } catch {
      return { isAuthenticated: false, username: '', token: '' };
    }
  }

  async authenticated(): Promise<boolean> {
    const token = TokenStorage.get();
    if (!token) {
      return false;
    }
    try {
      const payload = this.decodeJwt(token);
      const exp = payload.exp as number | undefined;
      if (exp && Date.now() >= exp * 1000) {
        TokenStorage.clear();
        return false;
      }
      return true;
    } catch {
      TokenStorage.clear();
      return false;
    }
  }

  async refreshToken(): Promise<string> {
    const token = TokenStorage.get();
    if (!token) {
      throw new Error('no_token');
    }
    return token;
  }

  getToken(): string | null {
    return TokenStorage.get();
  }

  private decodeJwt(token: string): Record<string, unknown> {
    const base64Url = token.split('.')[1];
    if (!base64Url) {
      throw new Error('invalid_token');
    }
    const base64 = base64Url.replace(/-/g, '+').replace(/_/g, '/');
    const jsonPayload = decodeURIComponent(
      atob(base64)
        .split('')
        .map(c => `%${`00${c.charCodeAt(0).toString(16)}`.slice(-2)}`)
        .join(''),
    );
    return JSON.parse(jsonPayload);
  }
}
