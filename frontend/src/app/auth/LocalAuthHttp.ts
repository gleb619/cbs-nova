import { AxiosHttp } from '@cbs/admin-plugin/composables/http/AxiosHttp';
import type { AxiosInstance } from 'axios';

export class LocalAuthHttp {
  private readonly http: AxiosHttp;

  constructor(axiosInstance: AxiosInstance) {
    this.http = new AxiosHttp(axiosInstance);
  }

  async login(username: string, password: string): Promise<string> {
    const config = {
      headers: { 'Content-Type': 'application/json' },
    };
    const response = await this.http.post<{ access_token: string }, { username: string; password: string }>(
      '/api/public/auth/token',
      { username, password },
      config,
    );
    return response.data.access_token;
  }

  async get<T>(uri: string, token: string): Promise<T> {
    const response = await this.http.get<T>(uri, {
      headers: { Authorization: `Bearer ${token}` },
    });
    return response.data;
  }

  async post<T, Payload = never>(uri: string, data: Payload, token: string): Promise<T> {
    const response = await this.http.post<T, Payload>(uri, data, {
      headers: { Authorization: `Bearer ${token}` },
    });
    return response.data;
  }
}
