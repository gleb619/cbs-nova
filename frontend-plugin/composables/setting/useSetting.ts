import { inject, ref } from 'vue';
import { HTTP_KEY } from '../http/createHttp';
import type { Setting } from './Setting';
import { fetchSettings } from './setting.service';

export function useSetting() {
  const http = inject(HTTP_KEY)!;
  const settings = ref<Setting[]>([]);
  const loading = ref(false);
  const error = ref<string | null>(null);

  const load = async (): Promise<void> => {
    loading.value = true;
    error.value = null;
    try {
      settings.value = await fetchSettings(http);
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'An error occurred while fetching settings.';
    } finally {
      loading.value = false;
    }
  };

  return { settings, loading, error, load };
}