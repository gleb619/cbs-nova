import type { RouteRecordRaw } from 'vue-router';
import MassOpDetailPage from './MassOpDetailPage.vue';
import MassOpListPage from './MassOpListPage.vue';

export const massOpRoutes = (): RouteRecordRaw[] => [
  { path: 'mass-operations', name: 'MassOpList', component: MassOpListPage, meta: { requiredRoles: ['ROLE_ADMIN'] } },
  { path: 'mass-operations/:id', name: 'MassOpDetail', component: MassOpDetailPage, meta: { requiredRoles: ['ROLE_ADMIN'] } },
];
