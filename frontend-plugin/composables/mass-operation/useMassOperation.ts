import { inject, ref } from 'vue';
import { HTTP_KEY } from '../http/createHttp';
import type { MassOperation } from './MassOperation';
import type { MassOperationItem } from './MassOperationItem';
import {
  fetchMassOperation,
  fetchMassOperations,
  fetchMassOperationItems,
  triggerMassOperation,
  retryFailedMassOperation,
} from './mass-operation.service';
import type { TriggerRequest, RetryResult } from './mass-operation.service';

export function useMassOperation() {
  const http = inject(HTTP_KEY)!;
  const massOperations = ref<MassOperation[]>([]);
  const massOperation = ref<MassOperation | null>(null);
  const items = ref<MassOperationItem[]>([]);
  const loading = ref(false);
  const itemsLoading = ref(false);
  const error = ref<string | null>(null);
  const itemsError = ref<string | null>(null);
  const retryResult = ref<string | null>(null);

  const loadAll = async (): Promise<void> => {
    loading.value = true;
    error.value = null;
    try {
      massOperations.value = await fetchMassOperations(http);
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load mass operations';
    } finally {
      loading.value = false;
    }
  };

  const loadOne = async (id: number): Promise<void> => {
    loading.value = true;
    error.value = null;
    try {
      massOperation.value = await fetchMassOperation(http, id);
    } catch (e: unknown) {
      error.value = e instanceof Error ? e.message : 'Failed to load mass operation';
    } finally {
      loading.value = false;
    }
  };

  const loadItems = async (id: number): Promise<void> => {
    itemsLoading.value = true;
    itemsError.value = null;
    try {
      items.value = await fetchMassOperationItems(http, id);
    } catch (e: unknown) {
      itemsError.value = e instanceof Error ? e.message : 'Failed to load items';
    } finally {
      itemsLoading.value = false;
    }
  };

  const trigger = async (request: TriggerRequest): Promise<void> => {
    await triggerMassOperation(http, request);
  };

  const retryFailed = async (id: number): Promise<RetryResult> => {
    return retryFailedMassOperation(http, id);
  };

  return {
    massOperations,
    massOperation,
    items,
    loading,
    itemsLoading,
    error,
    itemsError,
    retryResult,
    loadAll,
    loadOne,
    loadItems,
    trigger,
    retryFailed,
  };
}