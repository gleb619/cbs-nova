import { executionRoutes } from '@/execution/router';
import { massOpRoutes } from '@/mass-operation/router';
import type { RouteRecordRaw } from 'vue-router';
import Dashboard from './Dashboard.vue';
import Homepage from './Homepage.vue';
import SettingsPage from './SettingsPage.vue';

export const homeRoutes = (): RouteRecordRaw[] => [
  { path: '/', redirect: { name: 'Homepage' } },
  {
    path: '/home',
    name: 'Homepage',
    component: Homepage,
    redirect: { name: 'Dashboard' },
    children: [
      { path: 'dashboard', name: 'Dashboard', component: Dashboard },
      { path: 'settings', name: 'Settings', component: SettingsPage, meta: { requiredRoles: ['ROLE_ADMIN'] } },
      ...executionRoutes(),
      ...massOpRoutes(),
    ],
  },
];
