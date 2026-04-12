import { isLocalAuthMode } from '@/auth/application/AuthConfig';
import { helpRoutes } from '@/help/application/HelpRouter';
import { homeRoutes } from '@/home/application/HomeRouter';
import IndexPageVue from '@cbs/admin-plugin/composables/auth/IndexPageVue.vue';
import LoginPageVue from '@cbs/admin-plugin/composables/auth/LoginPageVue.vue';
import { useAuth } from '@cbs/admin-plugin/composables/auth/useAuth';
import { createRouter, createWebHistory } from 'vue-router';

export const routes = [
  // Landing page - accessible without authentication
  {
    path: '/',
    name: 'index',
    component: IndexPageVue,
  },
  // Help route - accessible without authentication
  ...helpRoutes(),
  ...homeRoutes(),
  {
    path: '/login',
    name: 'login',
    component: LoginPageVue,
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async to => {
  const auth = useAuth();

  const publicPaths = ['/', '/help', '/privacy', '/terms'];

  // Allow access to public pages without authentication
  if (publicPaths.includes(to.path)) {
    return true;
  }

  if (to.path === '/login') {
    const isAuth = await auth.authenticated();
    if (isAuth) {
      return { path: '/' };
    }
    return true;
  }

  const isAuth = await auth.authenticated();
  if (!isAuth) {
    if (isLocalAuthMode()) {
      return { path: '/login' };
    }
    await auth.login();
    return false;
  }

  return true;
});

export default router;
