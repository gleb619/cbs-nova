import { TokenStorage } from './TokenStorage';

/**
 * Decodes the JWT token from TokenStorage and returns the roles claim.
 * Returns an empty array if the token is absent, malformed, or the roles claim is missing.
 */
export function useRoles(): string[] {
  const token = TokenStorage.get();
  if (!token) return [];
  try {
    const payload = JSON.parse(atob(token.split('.')[1]));
    return Array.isArray(payload.roles) ? payload.roles : [];
  } catch {
    return [];
  }
}
