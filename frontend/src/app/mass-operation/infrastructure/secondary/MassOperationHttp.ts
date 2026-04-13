import { TokenStorage } from '@cbs/admin-plugin/composables/auth/TokenStorage';
import type { AxiosHttp } from '@cbs/admin-plugin/composables/http/AxiosHttp';
import type { MassOperation } from '@cbs/admin-plugin/composables/mass-operation/MassOperation';
import type { MassOperationItem } from '@cbs/admin-plugin/composables/mass-operation/MassOperationItem';
import type {
  MassOperationRepository,
  RetryResult,
  TriggerRequest,
} from '@cbs/admin-plugin/composables/mass-operation/MassOperationRepository';

export class MassOperationHttp implements MassOperationRepository {
  constructor(private readonly http: AxiosHttp) {}

  async findAll(): Promise<MassOperation[]> {
    const token = TokenStorage.get();
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const response = await this.http.get<MassOperation[]>('/api/mass-operations', { headers });
    return response.data;
  }

  async findById(id: number): Promise<MassOperation> {
    const token = TokenStorage.get();
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const response = await this.http.get<MassOperation>(`/api/mass-operations/${id}`, { headers });
    return response.data;
  }

  async findItems(id: number): Promise<MassOperationItem[]> {
    const token = TokenStorage.get();
    const headers = token ? { Authorization: `Bearer ${token}` } : {};
    const response = await this.http.get<MassOperationItem[]>(`/api/mass-operations/${id}/items`, { headers });
    return response.data;
  }

  async trigger(request: TriggerRequest): Promise<MassOperation> {
    const token = TokenStorage.get();
    const headers = token
      ? { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
      : { 'Content-Type': 'application/json' };
    const response = await this.http.post<MassOperation, TriggerRequest>('/api/mass-operations/trigger', request, { headers });
    return response.data;
  }

  async retryFailed(id: number): Promise<RetryResult> {
    const token = TokenStorage.get();
    const headers = token
      ? { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' }
      : { 'Content-Type': 'application/json' };
    const response = await this.http.post<RetryResult>(`/api/mass-operations/${id}/retry`, undefined, { headers });
    return response.data;
  }
}
