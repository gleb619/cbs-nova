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
  },
  {
    path: '/settings',
    name: 'Settings',
    component: SettingsPageVue,
  },
];
