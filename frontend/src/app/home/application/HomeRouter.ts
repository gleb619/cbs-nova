import DashboardView from '@/home/infrastructure/primary/DashboardView.vue';
import HomepageVue from '@/home/infrastructure/primary/HomepageVue.vue';
import SettingsPageVue from '@/home/infrastructure/primary/SettingsPageVue.vue';
import { executionRoutes } from '@/execution/application/ExecutionRouter';
import { massOpRoutes } from '@/mass-operation/application/MassOpRouter';
import type { RouteRecordRaw } from 'vue-router';

export const homeRoutes = (): RouteRecordRaw[] => [
  {
    path: '/',
    redirect: { name: 'Homepage' },
  },
  {
    path: '/home',
    name: 'Homepage',
    component: HomepageVue,
    redirect: { name: 'Dashboard' },
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: DashboardView,
      },
      {
        path: 'settings',
        name: 'Settings',
        component: SettingsPageVue,
        meta: { requiredRoles: ['ROLE_ADMIN'] },
      },
      // Execution routes (nested under /home)
      ...executionRoutes(),
      // Mass operation routes (nested under /home)
      ...massOpRoutes(),
    ],
  },
];
