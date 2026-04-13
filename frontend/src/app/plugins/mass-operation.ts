import { AxiosHttp } from '@cbs/admin-plugin/composables/http/AxiosHttp';
import { provideForMassOperation } from '@cbs/admin-plugin/composables/mass-operation/MassOperationProvider';
import axios from 'axios';
import { defineNuxtPlugin } from 'nuxt/app';
import { MassOperationHttp } from '../mass-operation/infrastructure/secondary/MassOperationHttp';

export default defineNuxtPlugin(() => {
  const config = useRuntimeConfig();
  const repository = new MassOperationHttp(new AxiosHttp(axios.create({ baseURL: config.public.apiBase as string })));
  provideForMassOperation(repository);
});
