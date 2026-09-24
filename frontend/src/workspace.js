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

async function refreshWorkspaces() {
  const items = await workspaceApi.list();
  state.items = items;
  state.initialized = true;
  state.current = items.find((item) => item.id === state.current?.id) || items[0] || null;
  return items;
}

async function createWorkspace(name) {
  const created = await workspaceApi.create({ name });
  await refreshWorkspaces();
  return created;
}

async function activateWorkspace(workspaceId) {
  const selected = await workspaceApi.get(Number(workspaceId));
  const index = state.items.findIndex((item) => item.id === selected.id);
  if (index < 0) state.items.push(selected);
  else state.items[index] = selected;
  state.current = selected;
  return selected;
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
  refreshWorkspaces,
  createWorkspace,
  activateWorkspace,
  selectWorkspace,
  hasPermission,
  clear
};
