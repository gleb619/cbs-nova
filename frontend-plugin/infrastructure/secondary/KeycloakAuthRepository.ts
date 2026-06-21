import type { AuthenticatedUser } from '../../composables/auth/AuthenticatedUser';
import type { AuthLike, Credentials } from '../../composables/auth/auth.store';
import type { KeycloakHttp } from './KeycloakHttp';

export class KeycloakAuthRepository implements AuthLike {
  constructor(private readonly keycloakHttp: KeycloakHttp) {}

  currentUser(): Promise<AuthenticatedUser> {
    return this.keycloakHttp.currentUser();
  }

  login(_credentials?: Credentials): Promise<void> {
    return this.keycloakHttp.login();
  }

  logout(): Promise<void> {
    return this.keycloakHttp.logout();
  }

  authenticated(): Promise<boolean> {
    return this.keycloakHttp.authenticated();
  }

  refreshToken(): Promise<string> {
    return this.keycloakHttp.refreshToken();
  }
}
