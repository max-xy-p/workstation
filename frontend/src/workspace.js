import { computed, reactive, readonly } from 'vue';
import { workspaceApi } from './api/workstation';

const state = reactive({
  items: [],
  current: null,
  initialized: false,
  loading: false
});

let loadingPromise = null;

async function ensureWorkspace(preferredId = null) {
  if (!state.initialized) {
    if (!loadingPromise) {
      state.loading = true;
      loadingPromise = workspaceApi.list()
        .then((items) => {
          state.items = items;
          state.initialized = true;
          return items;
        })
        .finally(() => {
          state.loading = false;
          loadingPromise = null;
        });
    }
    await loadingPromise;
  }

  const numericId = preferredId === null ? null : Number(preferredId);
  const preferred = Number.isSafeInteger(numericId)
    ? state.items.find((item) => item.id === numericId)
    : null;
  state.current = preferred || state.current || state.items[0] || null;
  return state.current;
}

function selectWorkspace(workspaceId) {
  const numericId = Number(workspaceId);
  const selected = state.items.find((item) => item.id === numericId) || null;
  state.current = selected;
  return selected;
}

function clear() {
  state.items = [];
  state.current = null;
  state.initialized = false;
  state.loading = false;
  loadingPromise = null;
}

const currentId = computed(() => state.current?.id ?? null);

function hasPermission(permission) {
  return state.current?.permissions?.includes(permission) === true;
}

export const workspace = {
  state: readonly(state),
  currentId,
  ensureWorkspace,
  selectWorkspace,
  hasPermission,
  clear
};
