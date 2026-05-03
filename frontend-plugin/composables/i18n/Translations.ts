import type { Resource, ResourceKey, ResourceLanguage } from 'i18next';
import i18next from 'i18next';

export type Translation = Record<string, unknown>;
export type Translations = Record<string, Translation>;

const toLanguage = ([key, value]: [string, ResourceKey]): [string, ResourceLanguage] => [
  key,
  {
    translation: value,
  },
];

export const mergeTranslations = (translations: Translations[]): Translations => {
  const result: Translations = {};
  for (const [key, translation] of translations.flatMap(t => Object.entries(t))) {
    result[key] = result[key] ? { ...result[key], ...deepMerge(result[key], translation) } : translation;
  }
  return result;
};

export const toTranslationResources = (...translations: Translations[]): Resource => {
  const merged = mergeTranslations(translations);
  const result: Resource = {};
  for (const [key, value] of Object.entries(merged).map(toLanguage)) {
    result[key] = value;
  }
  return result;
};

const deepMerge = (target: Translation, source: Translation): Translation => {
  for (const key of Object.keys(source)) {
    if (source[key] instanceof Object && key in target) {
      Object.assign(source[key], deepMerge(target[key] as Translation, source[key] as Translation));
    }
  }
  return { ...target, ...source };
};

let pendingTranslations: Translations[] = [];

export const registerTranslations = (translations: Translations): void => {
  if (i18next.isInitialized) {
    for (const [lang, ns] of Object.entries(translations)) {
      i18next.addResourceBundle(lang, 'translation', ns, true, true);
    }
  } else {
    pendingTranslations.push(translations);
    i18next.on('initialized', () => {
      for (const pending of pendingTranslations) {
        for (const [lang, ns] of Object.entries(pending)) {
          i18next.addResourceBundle(lang, 'translation', ns, true, true);
        }
      }
      pendingTranslations = [];
    });
  }
};
