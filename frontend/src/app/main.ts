import { toTranslationResources } from '@cbs/admin-plugin/composables/i18n/Translations';
import i18next from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import I18NextVue from 'i18next-vue';
import { createPinia } from 'pinia';
import piniaPersist from 'pinia-plugin-persistedstate';
import { createApp } from 'vue';
import AppVue from './AppVue.vue';
import { homeTranslations } from './home/HomeTranslations';
import router from './router';

// Initialize i18next with translations
void i18next.use(LanguageDetector).init({
  fallbackLng: 'en',
  debug: false,
  interpolation: {
    escapeValue: false,
  },
  resources: toTranslationResources(homeTranslations),
});

const app = createApp(AppVue);

app.use(I18NextVue, { i18next });

const pinia = createPinia();
pinia.use(piniaPersist);
app.use(pinia);

app.use(router);

export { app };
