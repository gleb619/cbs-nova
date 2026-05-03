import { key, piqureWrapper } from 'piqure';
import type { SettingRepository } from './SettingRepository';

const { provide, inject } = piqureWrapper(typeof window !== 'undefined' ? window : ({} as Window), 'piqure');

export const SETTING_REPOSITORY = key<SettingRepository>('SettingRepository');

export const provideForSetting = (repository: SettingRepository): void => {
  provide(SETTING_REPOSITORY, repository);
};

export { inject };
