import type Keycloak from 'keycloak-js';
import type { AuthenticatedUser } from '../../composables/auth/AuthenticatedUser';

export class KeycloakHttp {
  private initialized = false;

  constructor(private readonly keycloak: Keycloak) {}

  private async ensureInitialized(): Promise<void> {
    if (!this.initialized) {
      await this.keycloak.init({ onLoad: 'check-sso', checkLoginIframe: false });
      this.initialized = true;
    }
  }

  async currentUser(): Promise<AuthenticatedUser> {
    await this.ensureInitialized();
    if (this.keycloak.authenticated) {
      return {
        isAuthenticated: true,
        username: this.keycloak.tokenParsed?.preferred_username ?? '',
        token: this.keycloak.token ?? '',
      };
    }
    return { isAuthenticated: false, username: '', token: '' };
  }

  async login(): Promise<void> {
    await this.ensureInitialized();
    return this.keycloak.login();
  }

  async logout(): Promise<void> {
    await this.ensureInitialized();
    return this.keycloak.logout();
  }

  async authenticated(): Promise<boolean> {
    await this.ensureInitialized();
    return !!this.keycloak.token;
  }

  async refreshToken(): Promise<string> {
    await this.ensureInitialized();
    await this.keycloak.updateToken(5);
    return this.keycloak.token ?? '';
  }
}
