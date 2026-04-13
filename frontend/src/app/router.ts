import { isLocalAuthMode } from '@/auth/application/AuthConfig';
import { helpRoutes } from '@/help/application/HelpRouter';
import { homeRoutes } from '@/home/application/HomeRouter';
import IndexPageVue from '@cbs/admin-plugin/composables/auth/IndexPageVue.vue';
import LoginPageVue from '@cbs/admin-plugin/composables/auth/LoginPageVue.vue';
import { useAuth } from '@cbs/admin-plugin/composables/auth/useAuth';
import { useAbac } from '@/home/infrastructure/primary/sidebar/useAbac';
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
  // Backward compatibility: redirect /settings to /home/settings
  {
    path: '/settings',
    redirect: { name: 'Settings' },
  },
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

  // ABAC: check required roles for the route
  const abac = useAbac();
  const requiredRoles = to.meta.requiredRoles as string[] | undefined;
  if (requiredRoles && !abac.hasRole(requiredRoles)) {
    return { name: 'Homepage' };
  }

  return true;
});

export default router;
