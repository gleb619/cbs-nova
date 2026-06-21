import type { AxiosInstance } from 'axios';
import type { Setting } from './Setting';

export async function fetchSettings(http: AxiosInstance): Promise<Setting[]> {
  const response = await http.get<Setting[]>('/api/settings');
  return response.data;
}