import type { AxiosInstance } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import type { Setting } from './Setting';
import { fetchSettings } from './setting.service';

const mockHttp = (data: unknown): AxiosInstance =>
  ({ get: vi.fn().mockResolvedValue({ data }) } as unknown as AxiosInstance);

describe('setting.service', () => {
  it('should fetch settings from /api/settings', async () => {
    const settings: Setting[] = [{ id: 1, code: 'k', value: 'v', description: 'd' }];
    const http = mockHttp(settings);

    const result = await fetchSettings(http);

    expect(http.get).toHaveBeenCalledWith('/api/settings');
    expect(result).toEqual(settings);
  });
});