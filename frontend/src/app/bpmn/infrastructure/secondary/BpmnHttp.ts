import { TokenStorage } from '@cbs/admin-plugin/composables/auth/TokenStorage';
import type { BpmnRepository } from '@cbs/admin-plugin/composables/bpmn/BpmnRepository';
import type { AxiosHttp } from '@cbs/admin-plugin/composables/http/AxiosHttp';

export class BpmnHttp implements BpmnRepository {
  constructor(private readonly http: AxiosHttp) {}

  async fetchXml(code: string): Promise<string> {
    const token = TokenStorage.get();
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const response = await this.http.get<string>(`/api/workflows/${code}/bpmn`, { headers, responseType: 'text' });
    return response.data;
  }
}
