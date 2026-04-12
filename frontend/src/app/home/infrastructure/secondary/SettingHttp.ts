import { TokenStorage } from '@cbs/admin-plugin/composables/auth/TokenStorage';
import type { AxiosHttp } from '@cbs/admin-plugin/composables/http/AxiosHttp';
import type { Setting } from '@cbs/admin-plugin/composables/setting/Setting';
import type { SettingRepository } from '@cbs/admin-plugin/composables/setting/SettingRepository';

export class SettingHttp implements SettingRepository {
  constructor(private readonly http: AxiosHttp) {}

  async findAll(): Promise<Setting[]> {
    const token = TokenStorage.get();
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const response = await this.http.get<Setting[]>('/api/settings', { headers });
    return response.data;
  }
}
