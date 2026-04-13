import ExecutionDetailPageVue from '@/execution/infrastructure/primary/ExecutionDetailPageVue.vue';
import ExecutionListPageVue from '@/execution/infrastructure/primary/ExecutionListPageVue.vue';
import type { RouteRecordRaw } from 'vue-router';

export const executionRoutes = (): RouteRecordRaw[] => [
  { path: 'executions', name: 'ExecutionList', component: ExecutionListPageVue },
  { path: 'executions/:id', name: 'ExecutionDetail', component: ExecutionDetailPageVue },
];
