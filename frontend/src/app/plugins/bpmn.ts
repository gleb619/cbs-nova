import { provideForBpmn } from '@cbs/admin-plugin/composables/bpmn/BpmnProvider';
import { AxiosHttp } from '@cbs/admin-plugin/composables/http/AxiosHttp';
import axios from 'axios';
import { defineNuxtPlugin } from 'nuxt/app';
import { BpmnHttp } from '../bpmn/infrastructure/secondary/BpmnHttp';

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig();
  provideForBpmn(new BpmnHttp(new AxiosHttp(axios.create({ baseURL: config.public.apiBase as string }))));
});
