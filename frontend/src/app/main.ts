import i18next from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import I18NextVue from 'i18next-vue';
import { createPinia } from 'pinia';
import piniaPersist from 'pinia-plugin-persistedstate';
import { createApp } from 'vue';
import AppVue from './AppVue.vue';
import router from './router';

const app = createApp(AppVue);

// Initialize i18next
void i18next.use(LanguageDetector).init({
  fallbackLng: 'en',
  debug: false,
  interpolation: {
    escapeValue: false,
  },
});

app.use(I18NextVue, { i18next });

const pinia = createPinia();
pinia.use(piniaPersist);
app.use(pinia);

app.use(router);
app.mount('#app');
