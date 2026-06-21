import { inject } from 'vue';
import { HTTP_KEY } from '../http/createHttp';
import { fetchBpmnXml } from './bpmn.service';

export function useBpmn() {
  const http = inject(HTTP_KEY)!;

  const fetchXml = (code: string): Promise<string> => fetchBpmnXml(http, code);

  return { fetchXml };
}