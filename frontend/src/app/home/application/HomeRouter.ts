import DashboardView from '@/home/infrastructure/primary/DashboardView.vue';
import HomepageVue from '@/home/infrastructure/primary/HomepageVue.vue';
import SettingsPageVue from '@/home/infrastructure/primary/SettingsPageVue.vue';
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
      },
    ],
  },
];
