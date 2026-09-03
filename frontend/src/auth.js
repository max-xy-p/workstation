import { reactive, readonly } from 'vue';
import { ApiError, resetCsrfToken } from './api/client';
import { authApi } from './api/workstation';

const state = reactive({
  user: null,
  initialized: false,
  checking: false
});

let sessionCheck = null;

async function ensureSession() {
  if (state.initialized) {
    return state.user;
  }
  if (!sessionCheck) {
    state.checking = true;
    sessionCheck = authApi.currentUser()
      .then((user) => {
        state.user = user;
        return user;
      })
      .catch((error) => {
        if (!(error instanceof ApiError) || error.status !== 401) {
          console.error('无法检查登录状态', error);
        }
        state.user = null;
        return null;
      })
      .finally(() => {
        state.initialized = true;
        state.checking = false;
        sessionCheck = null;
      });
  }
  return sessionCheck;
}

async function login(username, password) {
  await authApi.login(username, password);
  state.user = await authApi.currentUser();
  state.initialized = true;
  return state.user;
}

async function logout() {
  try {
    await authApi.logout();
  } finally {
    clearSession();
  }
}

function clearSession() {
  state.user = null;
  state.initialized = true;
  resetCsrfToken();
}

export const auth = {
  state: readonly(state),
  ensureSession,
  login,
  logout,
  clearSession
};
