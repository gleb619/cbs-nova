import type { App, InjectionKey } from 'vue';
import type { AxiosInstance } from 'axios';

export const HTTP_KEY: InjectionKey<AxiosInstance> = Symbol('http');

export function installPlugin(app: App, http: AxiosInstance): void {
  app.provide(HTTP_KEY, http);
}