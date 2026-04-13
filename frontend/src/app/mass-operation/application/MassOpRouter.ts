import MassOpDetailPageVue from '@/mass-operation/infrastructure/primary/MassOpDetailPageVue.vue';
import MassOpListPageVue from '@/mass-operation/infrastructure/primary/MassOpListPageVue.vue';
import type { RouteRecordRaw } from 'vue-router';

export const massOpRoutes = (): RouteRecordRaw[] => [
  { path: 'mass-operations', name: 'MassOpList', component: MassOpListPageVue, meta: { requiredRoles: ['ROLE_ADMIN'] } },
  { path: 'mass-operations/:id', name: 'MassOpDetail', component: MassOpDetailPageVue, meta: { requiredRoles: ['ROLE_ADMIN'] } },
];
