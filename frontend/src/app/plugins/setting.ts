import { AxiosHttp } from '@cbs/admin-plugin/composables/http/AxiosHttp';
import { provideForSetting } from '@cbs/admin-plugin/composables/setting/SettingProvider';
import axios from 'axios';
import { defineNuxtPlugin } from 'nuxt/app';
import { SettingHttp } from '../home/infrastructure/secondary/SettingHttp';

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig();
  const repository = new SettingHttp(new AxiosHttp(axios.create({ baseURL: config.public.apiBase as string })));
  provideForSetting(repository);
});
