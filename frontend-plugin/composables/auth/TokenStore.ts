const STORAGE_KEY = 'local_auth_token';

let inMemoryToken: string | null = null;
let useLocalStorage = true;

const isLocalStorageAvailable = (): boolean => {
  try {
    const testKey = '__local_storage_test__';
    localStorage.setItem(testKey, 'test');
    localStorage.removeItem(testKey);
    return true;
  } catch {
    return false;
  }
};

useLocalStorage = isLocalStorageAvailable();

export const TokenStore = {
  get(): string | null {
    if (useLocalStorage) {
      return localStorage.getItem(STORAGE_KEY);
    }
    return inMemoryToken;
  },

  set(token: string): void {
    if (useLocalStorage) {
      localStorage.setItem(STORAGE_KEY, token);
    } else {
      inMemoryToken = token;
    }
  },

  clear(): void {
    if (useLocalStorage) {
      localStorage.removeItem(STORAGE_KEY);
    } else {
      inMemoryToken = null;
    }
  },

  reset(): void {
    inMemoryToken = null;
    if (useLocalStorage) {
      localStorage.removeItem(STORAGE_KEY);
    }
  },

  _setUseLocalStorage(value: boolean): void {
    useLocalStorage = value;
  },

  _getUseLocalStorage(): boolean {
    return useLocalStorage;
  },
};
