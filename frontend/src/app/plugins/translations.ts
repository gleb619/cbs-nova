import { registerTranslations } from '@cbs/admin-plugin/composables/i18n/Translations';
import { homeTranslations } from '../home/HomeTranslations';

export default defineNuxtPlugin(() => {
  registerTranslations(homeTranslations);
});
