import { provideForExecution } from '@cbs/admin-plugin/composables/execution/WorkflowExecutionProvider';
import { AxiosHttp } from '@cbs/admin-plugin/composables/http/AxiosHttp';
import axios from 'axios';
import { defineNuxtPlugin } from 'nuxt/app';
import { WorkflowExecutionHttp } from '../execution/infrastructure/secondary/WorkflowExecutionHttp';

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig();
  const repository = new WorkflowExecutionHttp(new AxiosHttp(axios.create({ baseURL: config.public.apiBase as string })));
  provideForExecution(repository);
});
