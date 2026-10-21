<template>
  <section class="webhook-panel">
    <header class="collection-heading"><div><h3>GitHub Issue 通知</h3><p>通知触发已关联来源刷新；未关联 Issue 不会创建任务，定时同步继续补齐遗漏。</p></div><button type="button" class="secondary" :disabled="busy" @click="openSettings">{{ config.configured ? '修改通知配置' : '配置通知' }}</button></header>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p class="meta">{{ config.enabled ? '通知接收已启用' : '通知接收未启用' }}</p>
    <p class="meta">接收与执行还要求连接已启用、原管理员授权和仓库关联有效。</p>
    <label v-if="endpoint">GitHub Payload URL<input :value="endpoint" readonly aria-label="Webhook 接收地址" /></label>
    <p class="meta">在 GitHub 手动配置此公开 HTTPS 地址，选择 application/json 和 Issues 事件，填写相同 Secret，并保持 SSL 验证。公网地址以实际部署域名为准。</p>
    <div class="collection-heading"><h4>已验签投递</h4><button type="button" class="secondary" :disabled="busy" @click="load">刷新投递</button></div>
    <article v-for="d in deliveries.items" :key="d.id" class="delivery-row"><strong>{{ statusLabel(d.status) }}</strong><p class="meta">{{ d.eventType }} / {{ d.action || '无动作' }} / 第 {{ d.attempts }} 轮 · {{ date(d.receivedAt) }}</p><p class="meta">投递：{{ d.deliveryKey }}{{ d.runId ? ' · 同步作业 #' + d.runId : '' }}</p><p v-if="d.lastError" class="error">{{ d.lastError }}</p><button v-if="['FAILED', 'CANCELLED'].includes(d.status)" type="button" class="secondary" :disabled="busy" @click="retry(d.id)">重试此投递</button></article>
    <p v-if="!deliveries.items.length" class="empty">尚无已验签投递。无效签名不会进入此列表。</p>
    <nav class="pagination" aria-label="Webhook 投递分页"><button type="button" class="secondary" :disabled="busy || deliveries.page === 0" @click="loadPage(deliveries.page - 1)">上一页</button><span>共 {{ deliveries.total }} 条</span><button type="button" class="secondary" :disabled="busy || !deliveries.hasMore" @click="loadPage(deliveries.page + 1)">下一页</button></nav>
    <AppDrawer v-model="opened" title="GitHub 通知配置" :busy="saving" :dirty="dirty" @closed="form.secret = ''">
      <p v-if="formError" class="error" role="alert">{{ formError }}</p>
      <form id="webhook-form" class="form-grid" @submit.prevent="save">
        <label class="check-field wide"><input v-model="form.enabled" type="checkbox" :disabled="saving" /><span>启用 GitHub 通知接收</span></label>
        <label class="wide">Webhook Secret<input v-model="form.secret" type="password" :disabled="saving" autocomplete="new-password" :required="!config.configured" minlength="32" maxlength="256" :placeholder="config.configured ? '留空沿用已有密钥' : '输入至少 32 个字符的随机密钥'" /></label>
        <p class="form-hint wide">Secret 加密保存且不回显；保存配置会取消旧通知作业。更换 Secret 后同步修改 GitHub 的配置。</p>
      </form>
      <template #footer="{ close }"><button type="button" class="secondary" :disabled="saving" @click="close">取消</button><button type="submit" form="webhook-form" :disabled="saving">{{ saving ? '保存中…' : '保存通知配置' }}</button></template>
    </AppDrawer>
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { webhookApi, type GithubWebhookResponse, type GithubWebhookDeliveryPageResponse } from '../api/workstation';
import AppDrawer from './AppDrawer.vue';
import { useFormPanel } from '../composables/useFormPanel.mjs';
const props = defineProps<{ workspaceId: number; connectionId: number }>();
const config = reactive<GithubWebhookResponse>({ configured: false, enabled: false, version: 0, endpointPath: null });
const deliveries = reactive<GithubWebhookDeliveryPageResponse>({ items: [], page: 0, size: 20, total: 0, hasMore: false });
const form = reactive({ enabled: true, secret: '' });
const { opened, saving, formError, dirty, openPanel, submit } = useFormPanel(form);
const error = ref(''); const busy = ref(false);
const endpoint = computed(() => config.endpointPath ? new URL(config.endpointPath, window.location.origin).href : '');
const statusLabel = (value: string) => ({ RECEIVED: '已接收', QUEUED: '已排队', RETRY: '等待重试', SUCCEEDED: '处理成功', FAILED: '重试耗尽', IGNORED: '已忽略', CANCELLED: '已取消' }[value] || value);
const date = (value: string) => new Date(value).toLocaleString();
function openSettings() { Object.assign(form, { enabled: config.configured ? config.enabled : true, secret: '' }); openPanel(); }
async function loadPage(page: number) {
  if (busy.value) return; busy.value = true;
  try { error.value = ''; Object.assign(deliveries, await webhookApi.deliveries(props.workspaceId, props.connectionId, page)); }
  catch (e) { error.value = e instanceof Error ? e.message : '读取投递失败'; }
  finally { busy.value = false; }
}
async function load() {
  if (busy.value) return;
  busy.value = true;
  try {
    error.value = '';
    Object.assign(config, await webhookApi.get(props.workspaceId, props.connectionId));
    Object.assign(deliveries, await webhookApi.deliveries(props.workspaceId, props.connectionId, deliveries.page));
  }
  catch (e) { error.value = e instanceof Error ? e.message : '读取通知配置失败'; }
  finally { busy.value = false; }
}
async function save() {
  const saved = await submit(async () => { Object.assign(config, await webhookApi.configure(props.workspaceId, props.connectionId, { enabled: form.enabled, expectedVersion: config.version, secret: form.secret || null })); });
  if (saved) { form.secret = ''; await load(); }
}
async function retry(id: number) {
  if (busy.value) return; busy.value = true;
  let succeeded = false;
  try { error.value = ''; await webhookApi.retry(props.workspaceId, props.connectionId, id); succeeded = true; }
  catch (e) { error.value = e instanceof Error ? e.message : '重试投递失败'; }
  finally { busy.value = false; }
  if (succeeded) await loadPage(deliveries.page);
}
onMounted(load);
</script>
<style scoped>
.webhook-panel { border-block-start: 1px solid var(--border); padding-block: 1rem; margin-block: 1rem; }
.webhook-panel .collection-heading { flex-wrap: wrap; gap: 12px; }
.webhook-panel .collection-heading > div { flex: 1 1 240px; }
.webhook-panel .collection-heading button { flex-shrink: 0; white-space: nowrap; }
.delivery-row { border-bottom: 1px solid var(--border); padding-block: .75rem; overflow-wrap: anywhere; }
</style>
