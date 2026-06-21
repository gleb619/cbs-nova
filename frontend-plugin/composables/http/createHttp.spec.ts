import { createApp } from 'vue';
import type { AxiosInstance } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import { HTTP_KEY, installPlugin } from './createHttp';

describe('createHttp', () => {
  it('should provide AxiosInstance under HTTP_KEY', () => {
    const app = createApp({});
    const fakeAxios = {} as AxiosInstance;

    installPlugin(app, fakeAxios);

    expect(app._context.provides[HTTP_KEY as symbol]).toBe(fakeAxios);
  });

  it('should expose HTTP_KEY as a Symbol', () => {
    expect(typeof HTTP_KEY).toBe('symbol');
  });
});