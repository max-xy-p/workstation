<template>
  <section class="page integrations-page">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="notice" role="status">{{ notice }}</p>
    <p v-if="!canManage" class="empty">只有工作空间所有者和管理员可以管理数据源。已导入的数据可在项目详情查看。</p>
    <template v-else>
      <div class="split workspace-split">
        <section class="panel collection-panel">
          <h2>代码托管连接</h2>
          <p class="meta">授权范围内的数据保存到当前工作空间；私有仓库导入后也按空间成员权限共享。</p>
          <article v-for="c in connections.items" :key="c.id" class="card">
            <div class="card-title"><button class="secondary" @click="choose(c)">{{ c.name }}</button><span class="badge">{{ c.enabled ? '已启用' : '已暂停' }}</span></div>
            <p>{{ c.provider }} · {{ c.account }} · {{ c.baseUrl }}</p>
            <p class="meta">上次成功同步：{{ date(c.lastSyncedAt) }} · {{ c.scheduleMinutes ? '每 ' + c.scheduleMinutes + ' 分钟' : '手动同步' }}</p>
            <p v-if="c.lastError" class="error">{{ c.lastError }}</p>
            <div class="actions">
              <button class="secondary" :disabled="busy" @click="edit(c)">编辑</button>
              <button class="secondary" :disabled="busy" @click="validate(c)">验证</button>
              <button class="secondary" :disabled="busy" @click="enable(c)">{{ c.enabled ? '暂停' : '启用' }}</button>
              <button class="danger" :disabled="busy" @click="remove(c)">移除</button>
            </div>
          </article>
          <p v-if="!connections.items.length" class="empty">添加连接后发现仓库，再选择导入或关联项目。</p>
          <nav class="pagination" aria-label="数据源分页">
            <button class="secondary" :disabled="connections.page === 0" @click="loadConnections(connections.page - 1)">上一页</button>
            <span>{{ connections.page + 1 }}</span>
            <button class="secondary" :disabled="!connections.hasMore" @click="loadConnections(connections.page + 1)">下一页</button>
          </nav>
        </section>
        <section class="panel form-panel">
          <h2>{{ editing ? '编辑连接' : '添加连接' }}</h2>
          <form class="form-grid" @submit.prevent="save">
            <label>名称<input v-model="form.name" required maxlength="120" /></label>
            <label>平台<select v-model="form.provider" :disabled="!!editing"><option value="GITHUB">GitHub</option><option value="GITLAB">GitLab</option></select></label>
            <label class="wide">实例地址<input v-model="form.baseUrl" type="url" :disabled="!!editing" required maxlength="500" /></label>
            <label class="wide">只读访问 Token<input v-model="form.token" type="password" autocomplete="new-password" :required="!editing" maxlength="4096" :placeholder="editing ? '留空保留现有凭证' : '输入平台访问凭证'" /></label>
            <label>仓库范围<select v-model="form.scope"><option value="OWNED">本人拥有</option><option value="ACCESSIBLE">本人及参与的组织/协作仓库</option></select></label>
            <label>自动同步间隔（分钟，0 为关闭）<input v-model.number="form.scheduleMinutes" type="number" min="0" max="10080" required /></label>
            <label><input v-model="form.includeForks" type="checkbox" />包含 Fork</label>
            <label><input v-model="form.includeArchived" type="checkbox" />包含归档仓库</label>
            <label class="wide"><input v-model="form.autoImport" type="checkbox" />自动为所选范围的所有新仓库创建项目</label>
            <p v-if="form.autoImport" class="meta wide">发现或定时同步时，每个未关联仓库会创建一个规划中的项目。</p>
            <label class="wide"><input v-model="form.sharingConfirmed" type="checkbox" :required="isShared" />我确认导入的数据（包括私有仓库）按当前空间成员权限共享</label>
            <div class="actions wide"><button :disabled="busy" type="submit">保存并验证</button><button type="button" class="secondary" @click="reset">新建连接</button></div>
          </form>
        </section>
      </div>
      <section v-if="current" class="panel">
        <div class="card-title"><h2>{{ current.name }} · 仓库发现与导入</h2>
          <div class="actions"><button :disabled="busy || !current.enabled || active" @click="trigger('discover')">发现仓库</button><button class="secondary" :disabled="busy || !current.enabled || active" @click="trigger('sync')">同步已关联仓库</button></div>
        </div>
        <p class="meta">仓库列表是已保存的发现结果。发现任务失败时，结果可能不完整；查看下方执行记录。</p>
        <div class="toolbar">
          <label>导入目标<select v-model="targetProject"><option :value="null">每个仓库新建项目</option><option v-for="p in projects" :key="p.id" :value="p.id">关联到 {{ p.name }}</option></select></label>
          <label>新项目状态<select v-model="projectStatus"><option v-for="s in PROJECT_STATUSES" :key="s.value" :value="s.value">{{ s.label }}</option></select></label>
          <button class="secondary" @click="selectPage">选择本页未关联仓库</button>
          <button :disabled="busy || active || !selectedIds.length" @click="imports">导入 {{ selectedIds.length }} 个仓库</button>
        </div>
        <label v-if="isShared"><input v-model="importConfirmed" type="checkbox" />确认所选仓库的数据对当前空间成员共享</label>
        <div class="repository-list">
          <article v-for="r in repositories.items" :key="r.id" class="card">
            <label><input v-model="selectedIds" :value="r.id" :disabled="!!r.projectId" type="checkbox" />{{ r.namespace }}/{{ r.name }}</label>
            <p class="meta">{{ r.visibility }} · {{ r.archived ? '已归档' : '未归档' }} · {{ r.language || '语言未提供' }}</p>
            <p>{{ r.description }}</p>
            <RouterLink v-if="r.projectId" :to="{ name: 'project-detail', params: { workspaceId, id: r.projectId } }">查看关联项目</RouterLink>
            <a :href="safeUrl(r.url)" target="_blank" rel="noopener noreferrer">查看仓库</a>
          </article>
        </div>
        <p v-if="!repositories.items.length" class="empty">暂无候选仓库，请先执行发现。</p>
        <nav class="pagination" aria-label="仓库分页"><button class="secondary" :disabled="repositories.page === 0" @click="loadRepositories(repositories.page - 1)">上一页</button><span>共 {{ repositories.total }} 个仓库</span><button class="secondary" :disabled="!repositories.hasMore" @click="loadRepositories(repositories.page + 1)">下一页</button></nav>
        <h3>同步执行记录</h3>
        <article v-for="r in runs.items" :key="r.id" class="card">
          <button class="secondary" @click="inspect(r)">{{ kindLabel(r.kind) }} · {{ statusLabel(r.status) }} · {{ date(r.createdAt) }}</button>
          <p>处理 {{ r.processed }} · 失败 {{ r.failed }} <span v-if="r.lastError" class="error">{{ r.lastError }}</span></p>
        </article>
        <nav class="pagination" aria-label="同步记录分页"><button class="secondary" :disabled="runs.page === 0" @click="loadRuns(runs.page - 1)">上一页</button><button class="secondary" :disabled="!runs.hasMore" @click="loadRuns(runs.page + 1)">下一页</button></nav>
        <div v-if="inspected"><h3>任务 {{ inspected.id }} 的导入明细</h3><p v-for="i in runItems.items" :key="i.id">仓库 {{ i.repositoryId }} · {{ statusLabel(i.status) }} · {{ i.lastError || '' }} <RouterLink v-if="i.projectId" :to="{ name: 'project-detail', params: { workspaceId, id: i.projectId } }">查看项目</RouterLink></p>
          <button v-if="inspected.status === 'PARTIAL' && runItems.items.some(i => i.status === 'FAILED')" :disabled="busy || active" @click="retryFailed">重试本页失败项</button>
          <nav class="pagination" aria-label="导入明细分页"><button class="secondary" :disabled="runItems.page === 0" @click="loadItems(runItems.page - 1)">上一页</button><button class="secondary" :disabled="!runItems.hasMore" @click="loadItems(runItems.page + 1)">下一页</button></nav>
        </div>
      </section>
    </template>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, reactive, ref, watch } from 'vue';
import { RouterLink, useRoute } from 'vue-router';
import { integrationApi, projectApi, type IntegrationConnectionRequest, type IntegrationConnectionResponse,
  type IntegrationConnectionPageResponse, type RepositoryPageResponse, type IntegrationRunPageResponse,
  type IntegrationRunItemPageResponse, type IntegrationRunResponse, type ProjectStatus, type ProjectSummaryResponse, type WorkspaceResponse } from '../api/workstation';
import { workspace } from '../workspace';
import { workspaceIdFromRoute } from '../utils/workspaceRoute';
import { PROJECT_STATUSES } from '../constants/options';
const route = useRoute();
const workspaceId = computed(() => workspaceIdFromRoute(route));
const canManage = computed(() => workspace.hasPermission('INTEGRATION_MANAGE'));
const isShared = computed(() => (workspace.state.current as WorkspaceResponse | null)?.type === 'SHARED');
const error = ref(''), notice = ref(''), busy = ref(false);
const connections = reactive<IntegrationConnectionPageResponse>({ items: [], page: 0, size: 20, total: 0, hasMore: false });
const repositories = reactive<RepositoryPageResponse>({ items: [], page: 0, size: 20, total: 0, hasMore: false });
const runs = reactive<IntegrationRunPageResponse>({ items: [], page: 0, size: 20, total: 0, hasMore: false });
const runItems = reactive<IntegrationRunItemPageResponse>({ items: [], page: 0, size: 20, total: 0, hasMore: false });
const current = ref<IntegrationConnectionResponse | null>(null), editing = ref<IntegrationConnectionResponse | null>(null);
const inspected = ref<IntegrationRunResponse | null>(null);
const projects = ref<ProjectSummaryResponse[]>([]), selectedIds = ref<number[]>([]);
const targetProject = ref<number | null>(null), projectStatus = ref<ProjectStatus>('PLANNING'), importConfirmed = ref(false);
const active = computed(() => runs.items.some(r => ['QUEUED', 'RUNNING'].includes(r.status)));
function defaults(): IntegrationConnectionRequest {
  return { name: '', provider: 'GITHUB', baseUrl: 'https://github.com', token: '', scope: 'OWNED', includeForks: false,
    includeArchived: false, autoImport: false, scheduleMinutes: 0, expectedVersion: null, sharingConfirmed: false };
}
const form = reactive(defaults());
watch(() => form.provider, p => { if (!editing.value) form.baseUrl = p === 'GITHUB' ? 'https://github.com' : 'https://gitlab.com'; });
const date = (v: string | null) => v ? new Date(v).toLocaleString() : '暂无';
const safeUrl = (v: string) => /^https?:\/\//i.test(v) ? v : '#';
const statusLabel = (s: string) => ({ QUEUED: '等待执行', RUNNING: '执行中', SUCCEEDED: '已完成', PARTIAL: '部分成功', FAILED: '失败', CANCELLED: '已取消' }[s] || s);
const kindLabel = (s: string) => ({ DISCOVER: '发现仓库', IMPORT: '导入仓库', SYNC: '同步开发情况' }[s] || s);
let disposed = false, polling = false;
async function action(task: () => Promise<unknown>) {
  busy.value = true; error.value = ''; notice.value = '';
  try { await task(); } catch (e) { error.value = e instanceof Error ? e.message : '操作失败'; }
  finally { busy.value = false; }
}
async function loadConnections(page = 0) { Object.assign(connections, await integrationApi.list(workspaceId.value, page)); }
async function loadRepositories(page = 0) { if (current.value) Object.assign(repositories, await integrationApi.repositories(workspaceId.value, current.value.id, page)); }
async function loadRuns(page = 0) { if (current.value) Object.assign(runs, await integrationApi.runs(workspaceId.value, current.value.id, page)); }
async function loadItems(page = 0) { if (current.value && inspected.value) Object.assign(runItems, await integrationApi.items(workspaceId.value, current.value.id, inspected.value.id, page)); }
async function choose(c: IntegrationConnectionResponse) {
  current.value = c; selectedIds.value = []; inspected.value = null;
  await action(async () => { await loadRepositories(); await loadRuns(); });
}
function edit(c: IntegrationConnectionResponse) { editing.value = c; Object.assign(form, c, { token: '', expectedVersion: c.version, sharingConfirmed: false }); }
function reset() { editing.value = null; Object.assign(form, defaults()); }
async function save() {
  const payload: IntegrationConnectionRequest = { name: form.name, provider: form.provider, baseUrl: form.baseUrl, token: form.token || null,
    scope: form.scope, includeForks: form.includeForks, includeArchived: form.includeArchived, autoImport: form.autoImport,
    scheduleMinutes: form.scheduleMinutes, expectedVersion: editing.value?.version ?? null, sharingConfirmed: form.sharingConfirmed };
  form.token = '';
  await action(async () => {
    const result = editing.value ? await integrationApi.update(workspaceId.value, editing.value.id, payload) : await integrationApi.create(workspaceId.value, payload);
    reset(); await loadConnections(); current.value = result; await loadRepositories(); await loadRuns(); notice.value = '连接已保存并验证，可开始发现仓库。';
  });
}
async function validate(c: IntegrationConnectionResponse) { await action(async () => { await integrationApi.validate(workspaceId.value, c.id); notice.value = '连接验证通过；开发数据权限会在同步时分别检查。'; }); }
async function enable(c: IntegrationConnectionResponse) {
  const confirmed = !isShared.value || c.enabled || window.confirm('启用后，所选范围的数据将对当前空间成员共享。是否继续？');
  if (!confirmed) return;
  await action(async () => { const updated = await integrationApi.enabled(workspaceId.value, c.id, { enabled: !c.enabled, expectedVersion: c.version, sharingConfirmed: confirmed }); await loadConnections(); if (current.value?.id === c.id) { current.value = updated; await loadRuns(); } });
}
async function remove(c: IntegrationConnectionResponse) {
  if (!window.confirm('移除连接将停止同步并删除凭证，已导入的项目和快照保留。是否继续？')) return;
  await action(async () => { await integrationApi.delete(workspaceId.value, c.id); if (current.value?.id === c.id) current.value = null; await loadConnections(); });
}
async function trigger(kind: 'discover' | 'sync') {
  if (!current.value) return;
  await action(async () => { await integrationApi[kind](workspaceId.value, current.value!.id, crypto.randomUUID()); await loadRuns(); });
}
function selectPage() { selectedIds.value = [...new Set([...selectedIds.value, ...repositories.items.filter(r => !r.projectId).map(r => r.id)])]; }
async function imports() {
  if (!current.value) return;
  if (selectedIds.value.length > 100) { error.value = '每次最多导入 100 个仓库；全部自动导入可在连接设置中启用。'; return; }
  await action(async () => {
    await integrationApi.imports(workspaceId.value, current.value!.id, { requestKey: crypto.randomUUID(),
      items: selectedIds.value.map(repositoryId => ({ repositoryId, projectId: targetProject.value, status: projectStatus.value })),
      sharingConfirmed: importConfirmed.value });
    selectedIds.value = []; await loadRuns();
  });
}
async function inspect(r: IntegrationRunResponse) { inspected.value = r; await action(() => loadItems()); }
async function retryFailed() {
  if (!current.value) return;
  await action(async () => {
    await integrationApi.imports(workspaceId.value, current.value!.id, { requestKey: crypto.randomUUID(),
      items: runItems.items.filter(i => i.status === 'FAILED').map(i => ({ repositoryId: i.repositoryId, projectId: i.projectId, status: i.projectStatus })),
      sharingConfirmed: importConfirmed.value });
    await loadRuns();
  });
}
const timer = setInterval(async () => {
  if (disposed || busy.value || polling || !current.value) return;
  polling = true;
  try {
    const wasActive = active.value;
    await loadRuns(runs.page);
    if (wasActive && !active.value) { await loadRepositories(repositories.page); await loadConnections(connections.page); }
    if (inspected.value) await loadItems(runItems.page);
  } catch (e) { if (!disposed) error.value = e instanceof Error ? e.message : '更新同步进度失败'; }
  finally { polling = false; }
}, 3000);
onBeforeUnmount(() => { disposed = true; clearInterval(timer); form.token = ''; });
onMounted(() => { if (canManage.value) void action(async () => { await loadConnections(); projects.value = await projectApi.listAll(workspaceId.value); }); });
</script>

<style scoped>
.repository-list { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 280px), 1fr)); gap: 1rem; margin: 1rem 0; }
.repository-list a { margin-right: 1rem; }
.form-grid input[type="checkbox"], .repository-list input[type="checkbox"] { width: auto; margin-right: .5rem; }
.toolbar { flex-wrap: wrap; }
</style>
