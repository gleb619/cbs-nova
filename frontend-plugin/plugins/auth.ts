import Keycloak from 'keycloak-js';
import { defineNuxtPlugin, useRuntimeConfig } from 'nuxt/app';
import { setAuth } from '../composables/auth/auth.store';
import { KeycloakAuthRepository } from '../infrastructure/secondary/KeycloakAuthRepository';
import { KeycloakHttp } from '../infrastructure/secondary/KeycloakHttp';

export default defineNuxtPlugin(async () => {
  const config = useRuntimeConfig();
  const localAuth = config.public.localAuth as boolean;

  if (localAuth) {
    const { setAuthConfig } = await import('@/auth/application/AuthConfig');
    const { LocalAuthRepository } = await import('@/auth/infrastructure/secondary/LocalAuthRepository');
    const { LocalAuthHttp } = await import('@/auth/infrastructure/secondary/LocalAuthHttp');
    const axios = await import('axios');

    setAuthConfig(true);

    const localAuthHttp = new LocalAuthHttp(axios.default.create({ baseURL: config.public.apiBase as string }));
    setAuth(new LocalAuthRepository(localAuthHttp));
  } else {
    const { setAuthConfig } = await import('@/auth/application/AuthConfig');
    setAuthConfig(false);

    const keycloak = new Keycloak({
      url: config.public.keycloakUrl as string,
      realm: config.public.keycloakRealm as string,
      clientId: config.public.keycloakClientId as string,
    });

    try {
      const keycloakHttp = new KeycloakHttp(keycloak);
      setAuth(new KeycloakAuthRepository(keycloakHttp));
    } catch (error) {
      throw new Error(`Keycloak initialisation failed: ${error instanceof Error ? error.message : String(error)}`);
    }
  }
});