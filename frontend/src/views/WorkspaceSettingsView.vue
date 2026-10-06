<template>
  <section class="page workspace-settings-page">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="notice" class="settings-notice" role="status">{{ notice }}</p>
    <div class="settings-layout">
      <section class="panel space-information" aria-labelledby="space-info-title">
        <span class="settings-avatar"><Users v-if="current?.type === 'SHARED'" /><UserRound v-else /></span>
        <h2 id="space-info-title">{{ current?.name }}</h2>
        <p class="settings-description">{{ workspaceTypeLabel(current?.type) }}</p>
        <dl class="space-facts"><div><dt>我的角色</dt><dd>{{ roleLabels[current?.role] }}</dd></div><div v-if="canReadMembers"><dt>成员</dt><dd>{{ members.length }} 人</dd></div></dl>
        <form v-if="canWriteWorkspace" class="space-name-form" @submit.prevent="renameWorkspace">
          <label>空间名称<input v-model="workspaceName" required maxlength="120" :disabled="renaming" /></label>
          <button type="submit" :disabled="renaming || !workspaceName.trim() || workspaceName.trim() === current?.name">{{ renaming ? '保存中…' : '保存名称' }}</button>
        </form>
        <p class="settings-description">从工作空间切换器中查找其他空间，或创建新的共享空间。</p>
      </section>

      <section v-if="canReadMembers" class="panel member-panel" aria-labelledby="member-title">
        <header class="member-heading"><div><h2 id="member-title">空间成员 <span>{{ members.length }}</span></h2><p>管理一起工作的人与他们的访问权限。</p></div><button v-if="canManageMembers" type="button" class="member-add-trigger" :aria-expanded="adding" @click="toggleAdding"><Plus v-if="!adding" /><X v-else />{{ adding ? '收起' : '添加成员' }}</button></header>
        <section v-if="adding && canManageMembers" class="member-discovery" aria-label="添加成员">
          <label class="member-search"><Search /><input ref="candidateInput" v-model="keyword" aria-label="搜索要添加的用户" placeholder="输入用户名或显示名称…" maxlength="255" /></label>
          <p class="member-search-hint" role="status">{{ candidateHint }}</p>
          <p v-if="candidateError" class="error" role="alert">{{ candidateError }}</p>
          <div v-for="candidate in candidates" :key="candidate.subjectId" class="member-row candidate-row">
            <span class="member-avatar">{{ initial(candidate) }}</span>
            <div class="member-copy"><strong>{{ displayName(candidate) }}</strong><small>{{ candidate.username }}</small></div>
            <template v-if="!isMember(candidate.subjectId)">
              <select v-model="candidateRoles[candidate.subjectId]" :disabled="pending.has(candidate.subjectId) || searching" :aria-label="'设置 ' + displayName(candidate) + ' 的角色'"><option v-for="role in roles" :key="role" :value="role">{{ roleLabels[role] }}</option></select>
              <button type="button" :disabled="pending.has(candidate.subjectId) || searching" @click="addMember(candidate)">{{ pending.has(candidate.subjectId) ? '添加中…' : '添加' }}</button>
            </template>
            <span v-else class="member-status"><Check />已加入</span>
          </div>
          <p class="settings-description">只查找已登录过本系统的用户。若找不到，请对方先登录一次。</p>
        </section>
        <div class="member-list-toolbar"><label class="member-search"><Search /><input v-model="memberFilter" aria-label="筛选当前成员" placeholder="筛选当前成员…" maxlength="255" /></label><span>{{ visibleMembers.length }} 位成员</span></div>
        <p v-if="loading" class="member-empty" role="status">正在加载成员…</p>
        <p v-else-if="!visibleMembers.length" class="member-empty">{{ memberFilter ? '没有匹配的成员。' : '暂无成员。' }}</p>
        <div v-for="member in visibleMembers" :key="member.subjectId" class="member-row" :aria-busy="pending.has(member.subjectId)">
          <span class="member-avatar">{{ initial(member) }}</span>
          <div class="member-copy"><strong>{{ displayName(member) }} <span v-if="member.subjectId === auth.state.user?.subjectId" class="member-you">你</span></strong><small>{{ member.username || (member.principalType === 'SERVICE' ? '机器身份' : '用户') }}</small></div>
          <select v-if="canEditMember(member)" :disabled="pending.has(member.subjectId)" :value="member.role" :aria-label="'修改 ' + displayName(member) + ' 的角色'" @change="changeRole(member, $event)"><option v-for="role in roles" :key="role" :value="role">{{ roleLabels[role] }}</option></select>
          <span v-else class="member-role">{{ roleLabels[member.role] }}</span>
          <button v-if="canEditMember(member)" class="member-remove" type="button" :disabled="pending.has(member.subjectId)" @click="requestRemoval(member)">移除</button>
          <span v-if="pending.has(member.subjectId)" class="member-row-progress" role="status">处理中…</span>
        </div>
      </section>
    </div>

    <Teleport to="body">
      <dialog ref="removeDialog" class="member-confirm-dialog" aria-labelledby="remove-title" @close="removeTarget = null" @cancel="cancelRemoval">
        <h2 id="remove-title">移除成员</h2>
        <p>确认移除「{{ removeTarget ? displayName(removeTarget) : '' }}」？移除后，该成员将无法访问这个工作空间。</p>
        <p>工作空间必须保留至少一位所有者，服务端会校验此限制。</p>
        <p v-if="removeError" class="error" role="alert">{{ removeError }}</p>
        <div class="member-confirm-actions"><button type="button" class="secondary" :disabled="removing" @click="removeDialog.close()">取消</button><button type="button" class="danger" :disabled="removing" @click="removeMember">{{ removing ? '移除中…' : '确认移除' }}</button></div>
      </dialog>
    </Teleport>
  </section>
</template>

<script setup>
import { Check, Plus, Search, UserRound, Users, X } from '@lucide/vue';
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { workspaceApi } from '../api/workstation';
import { workspace } from '../workspace';
import { auth } from '../auth';
import { workspaceIdFromRoute } from '../utils/workspaceRoute';
import { workspaceRoleLabels as roleLabels, workspaceTypeLabel } from '../utils/workspacePresentation';

const route = useRoute();
const router = useRouter();
const workspaceId = workspaceIdFromRoute(route);
const current = computed(() => workspace.state.current);
const canWriteWorkspace = computed(() => workspace.hasPermission('WORKSPACE_WRITE'));
const canReadMembers = computed(() => workspace.hasPermission('MEMBER_READ'));
const canManageMembers = computed(() => workspace.hasPermission('MEMBER_MANAGE'));
const roles = computed(() => current.value?.role === 'OWNER' ? ['OWNER', 'ADMIN', 'EDITOR', 'VIEWER'] : ['ADMIN', 'EDITOR', 'VIEWER']);
const members = ref([]);
const memberFilter = ref('');
const candidates = ref([]);
const candidateRoles = reactive({});
const pending = reactive(new Set());
const error = ref('');
const notice = ref('');
const loading = ref(false);
const renaming = ref(false);
const workspaceName = ref(current.value?.name || '');
const adding = ref(false);
const keyword = ref('');
const candidateInput = ref(null);
const searching = ref(false);
const searched = ref(false);
const candidateError = ref('');
const removeDialog = ref(null);
const removeTarget = ref(null);
const removing = ref(false);
const removeError = ref('');
let timer;
let searchSequence = 0;
let active = true;

const visibleMembers = computed(() => {
  const query = memberFilter.value.trim().toLocaleLowerCase();
  if (!query) return members.value;
  return members.value.filter((member) => [member.displayName, member.username].some((value) => value?.toLocaleLowerCase().includes(query)));
});
const candidateHint = computed(() => {
  if (!keyword.value.trim()) return '输入名称，查找你要添加的用户。';
  if (searching.value) return '正在搜索…';
  if (searched.value && !candidates.value.length) return '没有匹配的用户，试试其他名称。';
  return searched.value ? '请选择角色，然后添加成员。' : '';
});
function displayName(member) { return member.displayName || member.username || '未命名成员'; }
function initial(member) { return Array.from(displayName(member))[0].toLocaleUpperCase(); }
function isMember(subjectId) { return members.value.some((member) => member.subjectId === subjectId); }
function canEditMember(member) { return canManageMembers.value && (member.role !== 'OWNER' || current.value?.role === 'OWNER'); }

async function loadMembers() {
  loading.value = true;
  try {
    const result = canReadMembers.value ? await workspaceApi.listMembers(workspaceId) : [];
    if (active) members.value = result;
  } catch (err) { if (active) error.value = err.message; }
  finally { if (active) loading.value = false; }
}
async function renameWorkspace() {
  if (renaming.value) return;
  renaming.value = true;
  error.value = '';
  notice.value = '';
  try {
    await workspaceApi.update(workspaceId, { name: workspaceName.value.trim() });
    await workspace.refreshWorkspaces();
    workspaceName.value = current.value?.name || '';
    notice.value = '空间名称已保存。';
  } catch (err) { error.value = err.message; }
  finally { renaming.value = false; }
}
async function toggleAdding() {
  adding.value = !adding.value;
  if (adding.value) { await nextTick(); candidateInput.value?.focus(); }
  else keyword.value = '';
}

watch(keyword, () => {
  clearTimeout(timer);
  const sequence = ++searchSequence;
  const query = keyword.value.trim();
  candidateError.value = '';
  searched.value = false;
  candidates.value = [];
  searching.value = Boolean(query);
  if (query && canManageMembers.value) timer = setTimeout(() => searchCandidates(query, sequence), 300);
}, { flush: 'sync' });

async function searchCandidates(query, sequence) {
  try {
    const result = await workspaceApi.findMemberCandidates(workspaceId, query);
    if (!active || sequence !== searchSequence) return;
    candidates.value = result;
    for (const candidate of result) candidateRoles[candidate.subjectId] ??= 'EDITOR';
    searched.value = true;
  } catch (err) {
    if (active && sequence === searchSequence) candidateError.value = err.message;
  } finally { if (active && sequence === searchSequence) searching.value = false; }
}
async function memberOperation(subjectId, operation) {
  if (pending.has(subjectId)) return;
  pending.add(subjectId);
  error.value = '';
  notice.value = '';
  try { await operation(); }
  catch (err) { error.value = err.message; }
  finally { pending.delete(subjectId); }
}
function addMember(candidate) {
  return memberOperation(candidate.subjectId, async () => {
    const added = await workspaceApi.addMember(workspaceId, { subjectId: candidate.subjectId, role: candidateRoles[candidate.subjectId] });
    if (!active) return;
    members.value.push(added);
    notice.value = displayName(added) + ' 已加入空间。';
  });
}
function changeRole(member, event) {
  const role = event.target.value;
  event.target.value = member.role;
  if (role === member.role) return;
  return memberOperation(member.subjectId, async () => {
    const updated = await workspaceApi.updateMember(workspaceId, member.subjectId, role);
    if (!active) return;
    members.value = members.value.map((item) => item.subjectId === updated.subjectId ? updated : item);
    if (updated.subjectId === auth.state.user?.subjectId) await workspace.refreshWorkspaces();
    notice.value = displayName(updated) + ' 的角色已更新。';
  });
}
function requestRemoval(member) {
  removeTarget.value = member;
  removeError.value = '';
  removeDialog.value.showModal();
}
function cancelRemoval(event) { if (removing.value) event.preventDefault(); }
async function removeMember() {
  const member = removeTarget.value;
  if (!member || removing.value || pending.has(member.subjectId)) return;
  removing.value = true;
  pending.add(member.subjectId);
  removeError.value = '';
  try {
    await workspaceApi.removeMember(workspaceId, member.subjectId);
    if (!active) return;
    members.value = members.value.filter((item) => item.subjectId !== member.subjectId);
    removeDialog.value.close();
    notice.value = displayName(member) + ' 已移除。';
    if (member.subjectId === auth.state.user?.subjectId) {
      await workspace.refreshWorkspaces();
      await router.push(workspace.currentId.value ? { name: 'dashboard', params: { workspaceId: workspace.currentId.value } } : { name: 'workspace-unavailable' });
    }
  } catch (err) { removeError.value = err.message; }
  finally { removing.value = false; pending.delete(member.subjectId); }
}
onMounted(loadMembers);
onBeforeUnmount(() => { active = false; clearTimeout(timer); ++searchSequence; });
</script>

<style scoped>
.settings-layout { display: grid; grid-template-columns: minmax(240px, 300px) minmax(0, 1fr); align-items: start; gap: 24px; }
.space-information { padding: 24px; }
.settings-avatar { display: grid; place-items: center; width: 48px; height: 48px; border-radius: 12px; background: #e9eff6; color: var(--moss); margin-bottom: 18px; }
.settings-avatar svg { width: 24px; height: 24px; }
.space-information h2 { margin: 0 0 8px; overflow-wrap: anywhere; font-size: 21px; }
.settings-description { color: var(--muted); font-size: 12px; line-height: 1.7; }
.space-facts { margin: 22px 0; border-top: 1px solid var(--line); border-bottom: 1px solid var(--line); padding: 12px 0; }
.space-facts > div { display: flex; justify-content: space-between; gap: 12px; padding: 6px 0; font-size: 13px; }
.space-facts dt { color: var(--muted); }
.space-facts dd { margin: 0; }
.space-name-form { display: grid; gap: 12px; margin-bottom: 24px; }
.space-name-form label { display: grid; gap: 8px; font-size: 13px; }
.space-name-form button { justify-self: start; }
.member-panel { padding: 0; overflow: hidden; }
.member-heading { display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 24px; }
.member-heading h2 { margin: 0; font-size: 20px; }
.member-heading h2 span { color: var(--muted); font-size: 13px; font-weight: 500; margin-left: 6px; }
.member-heading p { margin: 6px 0 0; font-size: 12px; color: var(--muted); }
.member-add-trigger { display: flex; gap: 6px; align-items: center; flex: none; }
.member-add-trigger svg { width: 16px; height: 16px; }
.member-discovery { padding: 20px 24px; background: #f4f7fa; border-block: 1px solid var(--line); }
.member-search { display: flex; align-items: center; gap: 10px; background: white; border: 1px solid var(--line); border-radius: 8px; padding: 0 12px; min-width: 0; }
.member-search:focus-within { outline: 2px solid var(--moss); outline-offset: 2px; }
.member-search svg { width: 16px; height: 16px; flex: none; color: var(--muted); }
.member-search input { border: 0; box-shadow: none; outline: none; min-width: 0; padding: 10px 0; background: transparent; font-size: 13px; }
.member-search-hint { margin: 10px 0; min-height: 20px; font-size: 12px; color: var(--muted); }
.member-list-toolbar { display: flex; align-items: center; gap: 16px; padding: 16px 24px; border-bottom: 1px solid var(--line); }
.member-list-toolbar label { flex: 1; }
.member-list-toolbar > span { font-size: 12px; color: var(--muted); flex: none; }
.member-row { display: flex; flex-wrap: wrap; align-items: center; gap: 12px; padding: 16px 24px; border-bottom: 1px solid var(--line); }
.member-row:last-child { border-bottom: 0; }
.candidate-row { padding: 14px 0; }
.member-avatar { display: grid; place-items: center; width: 36px; height: 36px; flex: none; border-radius: 50%; color: var(--moss); background: #e8eef5; font-size: 14px; font-weight: 600; }
.member-copy { flex: 1; min-width: 100px; overflow-wrap: anywhere; }
.member-copy strong { display: block; font-size: 13px; }
.member-copy small { display: block; margin-top: 4px; font-size: 12px; color: var(--muted); }
.member-you { margin-left: 4px; font-size: 11px; font-weight: 400; color: var(--muted); }
.member-row select { width: 108px; padding: 8px; font-size: 12px; }
.member-row button { font-size: 12px; }
.member-remove { background: transparent; color: #b44646; border-color: transparent; box-shadow: none; }
.member-remove:hover { background: #fcf0f0; }
.member-status { display: flex; align-items: center; gap: 4px; color: var(--muted); font-size: 12px; }
.member-status svg { width: 14px; height: 14px; }
.member-role, .member-row-progress { color: var(--muted); font-size: 12px; }
.member-row-progress { flex-basis: 100%; text-align: right; }
.member-empty { padding: 28px 24px; color: var(--muted); font-size: 13px; }
.settings-notice { padding: 12px 16px; color: var(--moss-dark); background: #eaf3ef; border-radius: 8px; font-size: 13px; }
@media (max-width: 1180px) {
  .settings-layout { grid-template-columns: 1fr; }
}
@media (max-width: 620px) {
  .member-heading { flex-wrap: wrap; padding: 20px; }
  .member-discovery, .member-list-toolbar { padding: 16px 20px; }
  .member-row { padding: 14px 20px; gap: 8px; }
  .member-copy { min-width: 0; }
  .member-row select { width: 96px; }
  .member-remove { padding: 6px; }
  .candidate-row { padding: 14px 0; }
  .member-list-toolbar > span { display: none; }
}
</style>
<style>
.member-confirm-dialog { width: min(420px, calc(100vw - 32px)); padding: 24px; border: 1px solid var(--line); border-radius: 14px; background: white; color: var(--ink); }
.member-confirm-dialog h2 { margin-top: 0; font-size: 20px; }
.member-confirm-dialog p { font-size: 13px; line-height: 1.8; }
.member-confirm-actions { display: flex; justify-content: flex-end; gap: 12px; margin-top: 24px; }
</style>
