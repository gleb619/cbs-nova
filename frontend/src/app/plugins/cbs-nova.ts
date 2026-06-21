import { TokenStorage } from '@cbs/admin-plugin/composables/auth/TokenStorage';
import { installPlugin } from '@cbs/admin-plugin/composables/http/createHttp';
import axios from 'axios';
import { defineNuxtPlugin, useRuntimeConfig } from 'nuxt/app';

export default defineNuxtPlugin(nuxtApp => {
  const config = useRuntimeConfig();
  const instance = axios.create({ baseURL: config.public.apiBase as string });
  instance.interceptors.request.use(cfg => {
    const token = TokenStorage.get();
    if (token) cfg.headers.Authorization = `Bearer ${token}`;
    return cfg;
  });
  installPlugin(nuxtApp.vueApp, instance);
});
