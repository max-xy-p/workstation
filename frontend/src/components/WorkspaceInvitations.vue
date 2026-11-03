<template>
  <section class="panel invitation-panel" aria-labelledby="invitation-title">
    <h2 id="invitation-title">邮箱邀请</h2>
    <p class="muted">邀请尚未注册的同事，使用受邀邮箱登录并确认后加入。邀请有效期为 23 小时。</p>
    <form v-if="available" class="invitation-form" @submit.prevent="create">
      <label>收件邮箱<input v-model="email" type="email" autocomplete="off" required maxlength="254" :disabled="busy" /></label>
      <label>成员角色<select v-model="role" :disabled="busy"><option value="ADMIN">管理员</option><option value="EDITOR">编辑者</option><option value="VIEWER">只读成员</option></select></label>
      <button type="submit" :disabled="busy">{{ busy ? '提交中…' : '发送邀请' }}</button>
    </form>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <output v-if="notice" class="invitation-notice">{{ notice }}</output>
    <div class="invitation-toolbar"><span>最近 100 条邀请</span><button type="button" class="secondary" :disabled="loading" @click="load">{{ loading ? '刷新中…' : '刷新状态' }}</button></div>
    <p v-if="!items.length && !loading" class="muted">尚无邀请。</p>
    <div v-for="item in items" :key="item.id" class="invitation-row">
      <div><strong>{{ item.email }}</strong><small>{{ roles[item.role] }} · {{ statuses[item.status] }} · {{ mailStatuses[item.mailStatus] }}</small><small>到期：{{ new Date(item.expiresAt).toLocaleString() }}</small></div>
      <button v-if="item.status === 'PENDING'" type="button" class="secondary" :disabled="busy" @click="revoke(item)">撤销</button>
    </div>
  </section>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { invitationApi } from '../api/workstation';

const props = defineProps({ workspaceId: { type: Number, required: true } });
const email = ref('');
const role = ref('EDITOR');
const busy = ref(false);
const loading = ref(false);
const available = ref(false);
const items = ref([]);
const error = ref('');
const notice = ref('');
const roles = { ADMIN: '管理员', EDITOR: '编辑者', VIEWER: '只读成员' };
const statuses = { PENDING: '待接受', ACCEPTED: '已接受', REVOKED: '已撤销', EXPIRED: '已过期' };
const mailStatuses = { PENDING: '等待发信', PUBLISHED: '等待邮件服务接收', ACCEPTED: '邮件服务已接收', CANCELLED: '已停止投递', EXPIRED: '投递已过期' };
let active = true;
let pendingRequest = null;

async function load() {
  if (loading.value) return;
  loading.value = true;
  try {
    const result = await invitationApi.list(props.workspaceId);
    if (active) { items.value = result; available.value = true; error.value = ''; }
  } catch (err) { if (active) error.value = err.message; }
  finally { if (active) loading.value = false; }
}
async function create() {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  notice.value = '';
  const normalizedEmail = email.value.trim().toLowerCase();
  // 未知结果重试保留标识；用户修改内容则视为新请求。
  if (!pendingRequest || pendingRequest.email !== normalizedEmail || pendingRequest.role !== role.value) {
    pendingRequest = { requestId: crypto.randomUUID(), email: normalizedEmail, role: role.value };
  }
  try {
    await invitationApi.create(props.workspaceId, pendingRequest);
    if (!active) return;
    pendingRequest = null;
    email.value = '';
    notice.value = '邀请已创建，邮件将在后台发送。';
    await load();
  } catch (err) { if (active) error.value = err.message; }
  finally { if (active) busy.value = false; }
}
async function revoke(item) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    await invitationApi.revoke(props.workspaceId, item.id);
    if (!active) return;
    notice.value = '邀请已撤销，已发出的链接也无法加入空间。';
    await load();
  } catch (err) { if (active) error.value = err.message; }
  finally { if (active) busy.value = false; }
}
onMounted(load);
onBeforeUnmount(() => { active = false; });
</script>

<style scoped>
.invitation-panel { padding: 24px; margin-top: 24px; }
.invitation-panel h2 { margin-top: 0; font-size: 20px; }
.muted, small { color: var(--muted); font-size: 12px; line-height: 1.7; }
.invitation-form { display: flex; flex-wrap: wrap; align-items: end; gap: 12px; margin: 20px 0; }
.invitation-form label { display: grid; gap: 8px; flex: 1; min-width: 160px; font-size: 13px; }
.invitation-row, .invitation-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16px; padding: 14px 0; border-top: 1px solid var(--line); }
.invitation-row > div { min-width: 0; overflow-wrap: anywhere; }
.invitation-row small { display: block; }
.invitation-toolbar { margin-top: 16px; font-size: 12px; color: var(--muted); }
.invitation-notice { display: block; color: var(--moss-dark); }
</style>
