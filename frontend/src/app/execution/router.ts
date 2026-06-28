import type { RouteRecordRaw } from 'vue-router';
import ExecutionDetailPage from './ExecutionDetailPage.vue';
import ExecutionListPage from './ExecutionListPage.vue';

export const executionRoutes = (): RouteRecordRaw[] => [
  { path: 'executions', name: 'ExecutionList', component: ExecutionListPage },
  { path: 'executions/:id', name: 'ExecutionDetail', component: ExecutionDetailPage },
];
