import { registerTranslations } from '@cbs/admin-plugin/composables/i18n/Translations';
import { defineNuxtPlugin } from 'nuxt/app';
import { homeTranslations } from '../home/HomeTranslations';

export default defineNuxtPlugin(() => {
  registerTranslations(homeTranslations);
});
