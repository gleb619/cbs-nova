/**
 * Contract for JWT token persistence strategies.
 * Each implementation defines where and how the auth token is stored.
 */
export interface TokenStorage {
  /** Returns the stored JWT or null if absent. */
  get(): string | null;
  /** Persists a JWT. */
  set(token: string): void;
  /** Removes the stored JWT. */
  clear(): void;
}

/* In-Memory */

let _memoryToken: string | null = null;

export const InMemoryTokenStorage: TokenStorage = {
  get(): string | null {
    return _memoryToken;
  },
  set(token: string): void {
    _memoryToken = token;
  },
  clear(): void {
    _memoryToken = null;
  },
};

/* Session Storage */

const SESSION_KEY = 'cbs_nova_auth_token';

export const SessionStorageTokenStorage: TokenStorage = {
  get(): string | null {
    try {
      return sessionStorage.getItem(SESSION_KEY);
    } catch {
      return null;
    }
  },
  set(token: string): void {
    try {
      sessionStorage.setItem(SESSION_KEY, token);
    } catch {
      /* quota exceeded or private browsing — silently ignore */
    }
  },
  clear(): void {
    try {
      sessionStorage.removeItem(SESSION_KEY);
    } catch {
      /* ignore */
    }
  },
};

/* Default export (session) */

/**
 * Default token storage used across the app.
 * Currently defaults to sessionStorage so the token survives
 * page reloads but is automatically discarded when the tab closes.
 *
 * Swap to `InMemoryTokenStorage` for fully stateless testing.
 */
export const TokenStorage = SessionStorageTokenStorage;
