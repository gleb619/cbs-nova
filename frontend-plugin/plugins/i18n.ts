import { defineNuxtPlugin } from 'nuxt/app';
import i18next from 'i18next';
import LanguageDetector from 'i18next-browser-languagedetector';
import I18NextVue from 'i18next-vue';

export default defineNuxtPlugin(nuxtApp => {
  void i18next.use(LanguageDetector).init({
    fallbackLng: 'en',
    debug: false,
    interpolation: {
      escapeValue: false,
    },
  });

  nuxtApp.vueApp.use(I18NextVue, { i18next });

  return {
    provide: {
      i18next,
    },
  };
});
