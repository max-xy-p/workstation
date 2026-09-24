<template>
  <section class="page workspace-settings-page">
    <p v-if="error" class="error">{{ error }}</p>
    <div class="dashboard-grid">
      <section class="panel form-panel">
        <div class="section-heading"><div><span class="eyebrow">当前空间</span><h2>{{ current?.name }}</h2></div></div>
        <p>类型：{{ current?.type === 'SHARED' ? '共享工作空间' : '个人工作空间' }} · 我的角色：{{ roleLabels[current?.role] }}</p>
        <form v-if="canWriteWorkspace" class="form-grid" @submit.prevent="renameWorkspace">
          <label class="wide">工作空间名称<input v-model="workspaceName" required maxlength="120" /></label>
          <div class="actions wide"><button type="submit" :disabled="busy">保存名称</button></div>
        </form>
        <div class="section-heading form-section-heading"><div><span class="eyebrow">新建</span><h2>共享工作空间</h2></div></div>
        <form class="form-grid" @submit.prevent="createWorkspace">
          <label class="wide">名称<input v-model="newWorkspaceName" required maxlength="120" placeholder="例如：产品研发" /></label>
          <div class="actions wide"><button type="submit" :disabled="busy">创建并进入</button></div>
        </form>
      </section>

      <section v-if="canReadMembers" class="panel form-panel">
        <div class="section-heading"><div><span class="eyebrow">协作</span><h2>成员</h2></div></div>
        <div v-for="member in members" :key="member.subjectId" class="record-row">
          <div class="record-main">
            <strong>{{ member.displayName || member.username || member.subjectId }}</strong>
            <p>{{ member.username || member.subjectId }}</p>
          </div>
          <select v-if="canManageMembers && (member.role !== 'OWNER' || current?.role === 'OWNER')" :disabled="busy" :value="member.role" :aria-label="`修改 ${member.username || member.subjectId} 的角色`" @change="changeRole(member, $event)">
            <option v-for="role in roles" :key="role" :value="role">{{ roleLabels[role] }}</option>
          </select>
          <span v-else>{{ roleLabels[member.role] }}</span>
          <button v-if="canManageMembers && (member.role !== 'OWNER' || current?.role === 'OWNER')" type="button" :disabled="busy" class="danger" @click="removeMember(member)">移除</button>
        </div>

        <form v-if="canManageMembers" class="form-grid" @submit.prevent="findCandidate">
          <label class="wide">按准确用户名查找已登录过的用户<input v-model="username" required maxlength="255" /></label>
          <div class="actions wide"><button type="submit" :disabled="busy">查找用户</button></div>
        </form>
        <p v-if="searched && !candidates.length" class="empty">未找到用户，请对方先登录一次。</p>
        <div v-for="candidate in canManageMembers ? candidates : []" :key="candidate.subjectId" class="record-row">
          <div class="record-main"><strong>{{ candidate.displayName || candidate.username }}</strong><p>{{ candidate.username }}</p></div>
          <select v-model="candidateRoles[candidate.subjectId]" :aria-label="`设置 ${candidate.username} 的角色`">
            <option v-for="role in roles" :key="role" :value="role">{{ roleLabels[role] }}</option>
          </select>
          <button type="button" :disabled="busy || members.some((member) => member.subjectId === candidate.subjectId)" @click="addMember(candidate)">添加</button>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { workspaceApi } from '../api/workstation';
import { workspace } from '../workspace';
import { workspaceIdFromRoute } from '../utils/workspaceRoute';

const route = useRoute();
const router = useRouter();
const workspaceId = workspaceIdFromRoute(route);
const current = computed(() => workspace.state.current);
const canWriteWorkspace = computed(() => workspace.hasPermission('WORKSPACE_WRITE'));
const canReadMembers = computed(() => workspace.hasPermission('MEMBER_READ'));
const canManageMembers = computed(() => workspace.hasPermission('MEMBER_MANAGE'));
const roles = computed(() => current.value?.role === 'OWNER'
  ? ['OWNER', 'ADMIN', 'EDITOR', 'VIEWER'] : ['ADMIN', 'EDITOR', 'VIEWER']);
const roleLabels = { OWNER: '所有者', ADMIN: '管理员', EDITOR: '编辑者', VIEWER: '只读成员' };
const members = ref([]);
const candidates = ref([]);
const candidateRoles = reactive({});
const error = ref('');
const busy = ref(false);
const workspaceName = ref('');
const newWorkspaceName = ref('');
const username = ref('');
const searched = ref(false);

async function runOperation(operation) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    await operation();
  } catch (err) {
    error.value = err.message;
  } finally {
    busy.value = false;
  }
}

async function load() {
  workspaceName.value = current.value?.name || '';
  members.value = canReadMembers.value ? await workspaceApi.listMembers(workspaceId) : [];
}

function createWorkspace() {
  return runOperation(async () => {
    const created = await workspace.createWorkspace(newWorkspaceName.value);
    await router.push({ name: 'workspace-settings', params: { workspaceId: created.id } });
  });
}

function renameWorkspace() {
  return runOperation(async () => {
    await workspaceApi.update(workspaceId, { name: workspaceName.value });
    await workspace.refreshWorkspaces();
    await load();
  });
}

function findCandidate() {
  return runOperation(async () => {
    candidates.value = [];
    candidates.value = await workspaceApi.findMemberCandidates(workspaceId, username.value.trim());
    for (const candidate of candidates.value) candidateRoles[candidate.subjectId] = 'EDITOR';
    searched.value = true;
  });
}

function addMember(candidate) {
  return runOperation(async () => {
    await workspaceApi.addMember(workspaceId, {
      subjectId: candidate.subjectId, role: candidateRoles[candidate.subjectId]
    });
    candidates.value = candidates.value.filter((item) => item.subjectId !== candidate.subjectId);
    searched.value = false;
    await load();
  });
}

function changeRole(member, event) {
  const role = event.target.value;
  event.target.value = member.role;
  return runOperation(async () => {
    await workspaceApi.updateMember(workspaceId, member.subjectId, role);
    await workspace.refreshWorkspaces();
    await load();
  });
}

function removeMember(member) {
  if (!window.confirm('确认移除 ' + (member.displayName || member.username || member.subjectId) + '？')) return;
  return runOperation(async () => {
    await workspaceApi.removeMember(workspaceId, member.subjectId);
    await workspace.refreshWorkspaces();
    if (!workspace.state.items.some((item) => item.id === workspaceId)) {
      await router.push(workspace.currentId.value
        ? { name: 'dashboard', params: { workspaceId: workspace.currentId.value } }
        : { name: 'workspace-unavailable' });
      return;
    }
    await load();
  });
}

onMounted(() => { void runOperation(load); });
</script>

<style scoped>
.workspace-settings-page .record-row { display: flex; align-items: center; gap: 0.75rem; }
.workspace-settings-page .record-main { flex: 1; min-width: 0; }
.workspace-settings-page .record-main p { margin: 0.25rem 0 0; overflow-wrap: anywhere; }
.workspace-settings-page select { max-width: 9rem; }
</style>
