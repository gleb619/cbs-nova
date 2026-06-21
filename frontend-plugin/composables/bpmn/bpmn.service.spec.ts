import type { AxiosInstance } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import { fetchBpmnXml } from './bpmn.service';

const mockHttp = (data: unknown): AxiosInstance =>
  ({ get: vi.fn().mockResolvedValue({ data }) } as unknown as AxiosInstance);

describe('bpmn.service', () => {
  it('should fetch BPMN XML from /api/workflows/:code/bpmn as text', async () => {
    const xml = '<definitions/>';
    const http = mockHttp(xml);

    const result = await fetchBpmnXml(http, 'wf-001');

    expect(http.get).toHaveBeenCalledWith('/api/workflows/wf-001/bpmn', { responseType: 'text' });
    expect(result).toBe(xml);
  });
});