import type { AxiosInstance } from 'axios';

export async function fetchBpmnXml(http: AxiosInstance, code: string): Promise<string> {
  const response = await http.get<string>(`/api/workflows/${code}/bpmn`, { responseType: 'text' });
  return response.data;
}