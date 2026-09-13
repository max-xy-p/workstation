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

function login() {
  authApi.login();
}

async function logout() {
  await authApi.logout();
  clearSession();
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
