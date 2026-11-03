<template>
  <main class="invitation-page">
    <section class="panel invitation-card" aria-labelledby="invitation-heading">
      <p class="invitation-brand">Workstation · 空间邀请</p>
      <h1 id="invitation-heading">一起加入工作空间</h1>
      <p v-if="!token">邀请链接缺少凭据，请从邮件中重新打开完整链接。</p>
      <template v-else-if="!auth.state.user">
        <p>请使用收到邀请的邮箱登录或注册，验证邮箱后返回这里确认接受。</p>
        <button type="button" @click="login">登录或注册</button>
      </template>
      <template v-else>
        <p>当前账号：{{ auth.state.user.displayName || auth.state.user.username }}</p>
        <output v-if="loading">正在核对邀请…</output>
        <template v-if="invitation">
          <h2>{{ invitation.workspaceName }}</h2>
          <p>受邀邮箱：{{ invitation.email }}</p>
          <p>邀请角色：{{ roles[invitation.role] }} · {{ statuses[invitation.status] }}</p>
          <p>到期时间：{{ new Date(invitation.expiresAt).toLocaleString() }}</p>
          <button v-if="['PENDING', 'ACCEPTED'].includes(invitation.status)" type="button" :disabled="busy" @click="accept">{{ busy ? '处理中…' : invitation.status === 'ACCEPTED' ? '进入工作空间' : '接受邀请并加入' }}</button>
        </template>
        <div class="invitation-actions">
          <button type="button" class="secondary" :disabled="busy || loading" @click="preview">重新检查</button>
          <button type="button" class="secondary" :disabled="busy || loading" @click="switchAccount">切换账号</button>
          <RouterLink to="/">返回工作台</RouterLink>
        </div>
      </template>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
    </section>
  </main>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { invitationApi } from '../api/workstation';
import { auth } from '../auth';
import { workspace } from '../workspace';
import { readInvitationToken } from '../utils/invitationToken.mjs';

const route = useRoute();
const router = useRouter();
const token = ref(readInvitationToken(route.hash, sessionStorage));
if (route.hash) void router.replace({ name: 'invitation' });
const invitation = ref(null);
const loading = ref(false);
const busy = ref(false);
const error = ref('');
const roles = { ADMIN: '管理员', EDITOR: '编辑者', VIEWER: '只读成员' };
const statuses = { PENDING: '待接受', ACCEPTED: '已接受', REVOKED: '已撤销', EXPIRED: '已过期' };
let active = true;
let previewSequence = 0;

watch(() => route.hash, (hash) => {
  if (!hash) return;
  token.value = readInvitationToken(hash, sessionStorage);
  invitation.value = null;
  error.value = '';
  void router.replace({ name: 'invitation' });
  void preview();
});

function login() {
  sessionStorage.setItem('workstation:login-redirect', '/invitations');
  auth.login();
}
async function preview() {
  const sequence = ++previewSequence;
  if (!token.value || !auth.state.user) { loading.value = false; return; }
  loading.value = true;
  error.value = '';
  invitation.value = null;
  try {
    const result = await invitationApi.preview(token.value);
    if (active && sequence === previewSequence) invitation.value = result;
  } catch (err) { if (active && sequence === previewSequence) error.value = err.message; }
  finally { if (active && sequence === previewSequence) loading.value = false; }
}
async function accept() {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  const acceptingToken = token.value;
  try {
    const joined = await invitationApi.accept(acceptingToken);
    if (!active || token.value !== acceptingToken) return;
    await workspace.refreshWorkspaces();
    await router.push({ name: 'dashboard', params: { workspaceId: joined.id } });
    sessionStorage.removeItem('workstation:invitation-token');
  } catch (err) { if (active) error.value = err.message; }
  finally { if (active) busy.value = false; }
}
async function switchAccount() {
  busy.value = true;
  error.value = '';
  try {
    sessionStorage.setItem('workstation:login-redirect', '/invitations');
    await auth.logout();
  } catch (err) { error.value = err.message; busy.value = false; }
}
onMounted(preview);
onBeforeUnmount(() => { active = false; });
</script>

<style scoped>
.invitation-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: var(--paper); }
.invitation-card { width: min(100%, 540px); padding: 32px; overflow-wrap: anywhere; }
.invitation-brand { color: var(--moss); font-size: 13px; }
.invitation-card h1 { font-size: 26px; }
.invitation-card p { line-height: 1.8; }
.invitation-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; margin-top: 24px; }
</style>
