<template>
  <section class="page notification-page">
    <div class="panel notification-panel">
      <header class="notification-heading">
        <div><h2>通知中心</h2><p>查看各个工作空间中与你有关的更新。</p></div>
        <button type="button" class="secondary" :disabled="loading || Boolean(busy)" @click="refresh">刷新</button>
      </header>
      <p v-if="error || notifications.state.error" class="error" role="alert">{{ error || notifications.state.error }}</p>
      <output v-if="notice" class="notification-notice">{{ notice }}</output>
      <template v-if="notifications.state.enabled">
        <div class="notification-toolbar">
          <div class="notification-tabs" aria-label="通知范围">
            <button type="button" :class="{ secondary: unreadOnly }" :aria-pressed="!unreadOnly" :disabled="loading || Boolean(busy)" @click="filter(false)">全部</button>
            <button type="button" :class="{ secondary: !unreadOnly }" :aria-pressed="unreadOnly" :disabled="loading || Boolean(busy)" @click="filter(true)">未读 {{ notifications.state.unreadCount }}</button>
          </div>
          <button type="button" class="secondary" :disabled="loading || busy || !notifications.state.unreadCount" @click="readAll">全部标为已读</button>
        </div>
        <p v-if="notifications.state.connection === 'polling'" class="notification-hint">实时连接暂不可用，正在定期检查更新。</p>
        <button v-if="notifications.state.latestSequence > upper" type="button" class="secondary notification-update" :disabled="loading || Boolean(busy)" @click="refresh">有新通知，刷新查看</button>
        <output v-if="loading" class="notification-empty">正在加载通知…</output>
        <p v-else-if="!page.items.length" class="notification-empty">{{ unreadOnly ? '没有未读通知。' : '暂时没有通知，有新的更新时会显示在这里。' }}</p>
        <article v-for="item in page.items" :key="item.id" class="notification-item" :class="{ unread: !item.readAt }">
          <div class="notification-item-copy">
            <span v-if="!item.readAt" class="notification-dot" aria-label="未读"></span>
            <button type="button" class="notification-title" :disabled="Boolean(busy)" @click="open(item)">{{ item.title }}</button>
            <p>{{ item.body }}</p>
            <time :datetime="item.createdAt">{{ new Date(item.createdAt).toLocaleString() }}</time>
          </div>
          <button v-if="!item.readAt" type="button" class="secondary" :disabled="Boolean(busy)" @click="read(item)">{{ busy === item.id ? '处理中…' : '标为已读' }}</button>
          <span v-else class="notification-hint">已读</span>
        </article>
        <nav v-if="page.total > page.size" class="notification-pagination" aria-label="通知分页">
          <button type="button" class="secondary" :disabled="loading || busy || !page.page" @click="load(page.page - 1)">上一页</button>
          <span>{{ page.page + 1 }} / {{ Math.ceil(page.total / page.size) }}</span>
          <button type="button" class="secondary" :disabled="loading || busy || !page.hasMore" @click="load(page.page + 1)">下一页</button>
        </nav>
      </template>
      <p v-else-if="!loading && !notifications.state.loading && !notifications.state.error" class="notification-empty">通知中心尚未启用。</p>
    </div>
  </section>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { notificationApi } from '../api/workstation';
import { notifications } from '../notifications';
import { notificationTarget } from '../utils/notificationTarget.mjs';

const router = useRouter();
const page = ref({ items: [], page: 0, size: 20, total: 0, hasMore: false });
const upper = ref(0);
const unreadOnly = ref(false);
const loading = ref(false);
const busy = ref('');
const error = ref('');
const notice = ref('');
let active = true;
let sequence = 0;

async function fetchPage(number, requestSequence) {
  if (!notifications.state.enabled) { page.value = { ...page.value, items: [], total: 0 }; return; }
  const result = await notificationApi.list({ page: number, size: 20, unreadOnly: unreadOnly.value, upToSequence: upper.value });
  if (active && requestSequence === sequence) page.value = result;
}
async function load(number) {
  const requestSequence = ++sequence;
  loading.value = true;
  error.value = '';
  try { await fetchPage(number, requestSequence); }
  catch (err) { if (active && requestSequence === sequence) error.value = err.message; }
  finally { if (active && requestSequence === sequence) loading.value = false; }
}
async function refresh() {
  const requestSequence = ++sequence;
  loading.value = true;
  error.value = '';
  try {
    await notifications.refresh();
    if (!active || requestSequence !== sequence) return;
    upper.value = notifications.state.latestSequence;
    await fetchPage(0, requestSequence);
  } catch (err) { if (active && requestSequence === sequence) error.value = err.message; }
  finally { if (active && requestSequence === sequence) loading.value = false; }
}
async function filter(unread) {
  unreadOnly.value = unread;
  await refresh();
}
async function read(item) {
  if (busy.value) return;
  busy.value = item.id;
  error.value = '';
  try { await notificationApi.read(item.id); if (active) await refresh(); }
  catch (err) { if (active) error.value = err.message; }
  finally { if (active) busy.value = ''; }
}
async function readAll() {
  if (busy.value) return;
  busy.value = 'all';
  error.value = '';
  try {
    await notificationApi.readAll({ throughSequence: upper.value });
    if (!active) return;
    notice.value = '当前范围内的通知已标为已读，新到通知会继续保留未读。';
    await refresh();
  } catch (err) { if (active) error.value = err.message; }
  finally { if (active) busy.value = ''; }
}
async function open(item) {
  if (busy.value) return;
  busy.value = item.id;
  error.value = '';
  try {
    const current = await notificationApi.get(item.id);
    if (!active) return;
    const target = notificationTarget(current);
    if (!target) throw new Error('此通知没有可打开的站内资源。');
    await notificationApi.read(item.id);
    if (!active) return;
    await notifications.refresh();
    await router.push(target);
  } catch (err) { if (active) error.value = err.message; }
  finally { if (active) busy.value = ''; }
}
watch(() => notifications.state.refreshVersion, () => {
  if (active && !loading.value && !busy.value) {
    if (page.value.page === 0) { upper.value = notifications.state.latestSequence; void load(0); }
    else void load(page.value.page);
  }
});
onMounted(refresh);
onBeforeUnmount(() => { active = false; ++sequence; });
</script>

<style scoped>
.notification-panel { width: 100%; max-width: 960px; margin: 0 auto; padding: 24px; }
.notification-heading, .notification-toolbar, .notification-item, .notification-pagination { display: flex; justify-content: space-between; align-items: center; gap: 16px; }
.notification-heading { align-items: start; }
.notification-heading h2 { margin: 0 0 8px; font-size: 22px; }
.notification-heading p, .notification-hint { color: var(--muted); font-size: 12px; line-height: 1.7; }
.notification-toolbar { padding: 18px 0; border-bottom: 1px solid var(--line); flex-wrap: wrap; }
.notification-tabs { display: flex; gap: 8px; }
.notification-item { padding: 20px 0; border-bottom: 1px solid var(--line); align-items: start; }
.notification-item-copy { position: relative; min-width: 0; flex: 1; padding-left: 16px; overflow-wrap: anywhere; }
.notification-title { display: block; text-align: left; color: var(--ink); background: transparent; border: 0; box-shadow: none; padding: 0; font-size: 14px; font-weight: 500; }
.unread .notification-title { font-weight: 700; }
.notification-title:hover { color: var(--moss-dark); text-decoration: underline; }
.notification-dot { position: absolute; top: 7px; left: 0; width: 6px; height: 6px; background: var(--moss); border-radius: 50%; }
.notification-item p { white-space: pre-wrap; font-size: 13px; line-height: 1.8; margin: 8px 0; }
.notification-item time { color: var(--muted); font-size: 11px; }
.notification-item > button { flex: none; font-size: 12px; }
.notification-empty { display: block; padding: 36px 0; text-align: center; color: var(--muted); }
.notification-pagination { justify-content: flex-end; padding-top: 20px; font-size: 12px; }
.notification-notice { display: block; color: var(--moss-dark); padding: 12px 0; }
.notification-update { margin-top: 12px; }
@media (max-width: 620px) {
  .notification-panel { padding: 20px 16px; }
  .notification-item { flex-wrap: wrap; }
  .notification-item > button { margin-left: 16px; }
}
</style>
