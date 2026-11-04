import { reactive, readonly } from 'vue';
import { notificationApi, subscribeNotifications } from './api/workstation';
import { createNotificationRuntime } from './utils/notificationRuntime.mjs';

const state = reactive({ enabled: false, unreadCount: 0, latestSequence: 0, revision: 0,
  refreshVersion: 0, loading: false, error: '', connection: 'stopped' });
const runtime = createNotificationRuntime({ state, api: notificationApi, openStream: subscribeNotifications });
let listening = false;
function reconcile() { void runtime.refresh(); }
function visibilityChanged() { if (document.visibilityState === 'visible') reconcile(); }

function start(subject) {
  runtime.start(subject);
  if (subject && !listening) {
    window.addEventListener('focus', reconcile);
    document.addEventListener('visibilitychange', visibilityChanged);
    listening = true;
  } else if (!subject && listening) {
    window.removeEventListener('focus', reconcile);
    document.removeEventListener('visibilitychange', visibilityChanged);
    listening = false;
  }
}

export const notifications = {
  state: readonly(state),
  start,
  stop() { start(null); },
  refresh: runtime.refresh
};
