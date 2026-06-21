import { afterEach, describe, expect, it } from 'vitest';
import { setAuth, getAuth } from './auth.store';
import type { AuthLike } from './auth.store';

const stubRepo = (): AuthLike => ({
  currentUser: async () => ({ isAuthenticated: false, username: '', token: '' }),
  login: async () => {},
  logout: async () => {},
  authenticated: async () => false,
  refreshToken: async () => '',
});

describe('auth.store', () => {
  afterEach(() => {
    setAuth(null as unknown as AuthLike);
  });

  it('should store and return auth implementation', () => {
    const repo = stubRepo();
    setAuth(repo);
    expect(getAuth()).toBe(repo);
  });

  it('should throw when getAuth called before setAuth', () => {
    expect(() => getAuth()).toThrow('Auth not initialized');
  });
});