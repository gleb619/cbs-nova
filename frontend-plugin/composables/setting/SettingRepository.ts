import type { Setting } from './Setting';

export interface SettingRepository {
  findAll(): Promise<Setting[]>;
}
