<template>
  <section class="page integrations-page">
    <PageActions>
      <RouterLink v-if="isDetail" class="button secondary" :to="{ name: 'integrations', params: { workspaceId } }">连接列表</RouterLink>
      <button v-if="canManage && !isDetail" type="button" @click="newConnection">添加连接</button>
      <button v-if="canManage && current" type="button" class="secondary" @click="edit(current)">编辑连接</button>
    </PageActions>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <output v-if="notice" class="inline-notice">{{ notice }}</output>
    <p v-if="!canManage" class="empty">只有工作空间所有者和管理员可以管理数据源。已导入的数据可在项目详情查看。</p>
    <template v-else>
      <output v-if="loading" class="loading-state">正在读取数据源…</output>
      <section v-else-if="!isDetail" class="panel collection-panel">
        <header class="collection-heading"><div><h2>代码托管连接</h2><p>连接 GitHub 或 GitLab，在项目中查看已同步的开发情况。</p></div><span class="section-count">{{ connections.total }} 个连接</span></header>
        <div v-if="connections.items.length" class="connection-table">
          <div class="connection-table-heading" aria-hidden="true"><span>连接 / 账号</span><span>状态</span><span>最近成功同步</span><span>操作</span></div>
          <article v-for="c in connections.items" :key="c.id" class="connection-row">
            <div class="connection-identity"><RouterLink :to="detailRoute(c.id)"><strong>{{ c.name }}</strong></RouterLink><span class="meta">{{ providerLabel(c.provider) }} / {{ c.account }}</span><span class="meta connection-url">{{ c.baseUrl }}</span><p v-if="c.lastError" class="error">{{ c.lastError }}</p></div>
            <span class="badge" :class="{ 'status-paused': !c.enabled }">{{ c.enabled ? '已启用' : '已暂停' }}</span>
            <div class="connection-timing"><span>{{ date(c.lastSyncedAt) }}</span><small>{{ schedule(c.scheduleMinutes) }}</small></div>
            <div class="actions"><RouterLink class="button secondary" :to="detailRoute(c.id)">查看</RouterLink><details class="connection-menu"><summary aria-label="连接操作">更多</summary><div class="connection-menu-content"><button type="button" @click="edit(c)">编辑连接</button><button type="button" :disabled="busy" @click="validate(c)">验证连接</button><button type="button" :disabled="busy" @click="enable(c)">{{ c.enabled ? '暂停同步' : '启用同步' }}</button><button type="button" class="danger" :disabled="busy" @click="remove(c)">移除连接</button></div></details></div>
          </article>
        </div>
        <div v-else class="integration-empty"><h3>还没有连接代码托管平台</h3><p>添加连接后发现仓库，选择导入或关联到已有项目。</p><button type="button" @click="newConnection">添加第一个连接</button></div>
        <nav v-if="connections.total > connections.size" class="pagination" aria-label="数据源分页"><button class="secondary" :disabled="busy || connections.page === 0" @click="action(() => loadConnections(connections.page - 1))">上一页</button><span>第 {{ connections.page + 1 }} 页</span><button class="secondary" :disabled="busy || !connections.hasMore" @click="action(() => loadConnections(connections.page + 1))">下一页</button></nav>
      </section>

      <template v-else-if="current">
        <section class="panel connection-overview">
          <div class="collection-heading"><div><h2>{{ current.name }}</h2><p>{{ providerLabel(current.provider) }} / {{ current.account }} / {{ current.baseUrl }}</p></div><span class="badge">{{ current.enabled ? '已启用' : '已暂停' }}</span></div>
          <div class="connection-facts"><span>最近成功同步：{{ date(current.lastSyncedAt) }}</span><span>{{ schedule(current.scheduleMinutes) }}</span><output v-if="active">有任务等待或执行中</output></div>
          <p v-if="current.lastError" class="error">{{ current.lastError }}</p>
        </section>
        <nav class="integration-tabs" aria-label="连接内容">
          <button v-for="item in tabs" :key="item.value" type="button" :class="{ selected: tab === item.value }" :aria-current="tab === item.value ? 'page' : undefined" @click="tab = item.value">{{ item.label }}</button>
        </nav>

        <section v-if="tab === 'repositories'" class="panel collection-panel">
          <header class="collection-heading"><div><h2>仓库</h2><p>显示已保存的发现结果；执行失败或达到采集上限时，列表可能不完整。</p></div><div class="actions"><button type="button" class="secondary" :disabled="busy || !current.enabled || active" @click="trigger('sync')">同步已关联仓库</button><button type="button" :disabled="busy || !current.enabled || active" @click="trigger('discover')">发现仓库</button></div></header>
          <div v-if="repositories.items.length" class="repository-toolbar"><label class="check-field"><input type="checkbox" :checked="pageSelected" :indeterminate="pagePartiallySelected" :disabled="!selectableIds.length || busy" @change="togglePage" /><span>选择本页未关联仓库</span></label><span class="meta">共 {{ repositories.total }} 个仓库</span></div>
          <div class="repository-list">
            <article v-for="r in repositories.items" :key="r.id" class="repository-row">
              <label class="check-field repository-select"><input v-model="selectedIds" :value="r.id" :disabled="!!r.projectId || busy || (!selectedIds.includes(r.id) && selectedIds.length >= 100)" type="checkbox" /><span><strong>{{ r.namespace }}/{{ r.name }}</strong><small>{{ visibilityLabel(r.visibility) }} / {{ r.language || '语言未提供' }}{{ r.archived ? ' / 已归档' : '' }}</small></span></label>
              <p v-if="r.description" class="repository-description">{{ r.description }}</p>
              <div class="repository-links"><RouterLink v-if="r.projectId" :to="{ name: 'project-detail', params: { workspaceId, id: r.projectId } }">已关联项目</RouterLink><span v-else class="meta">未关联</span><a :href="safeUrl(r.url)" target="_blank" rel="noopener noreferrer">查看仓库</a></div>
            </article>
          </div>
          <div v-if="!repositories.items.length" class="integration-empty"><h3>暂无仓库发现结果</h3><p>点击“发现仓库”读取当前连接授权范围内的仓库。</p></div>
          <nav v-if="repositories.total > repositories.size" class="pagination" aria-label="仓库分页"><button class="secondary" :disabled="busy || repositories.page === 0" @click="action(() => loadRepositories(repositories.page - 1))">上一页</button><span>第 {{ repositories.page + 1 }} 页</span><button class="secondary" :disabled="busy || !repositories.hasMore" @click="action(() => loadRepositories(repositories.page + 1))">下一页</button></nav>
          <div v-if="selectedIds.length" class="selection-bar"><span>已选 {{ selectedIds.length }} 个仓库（含其他页）</span><div class="actions"><button type="button" class="secondary" :disabled="busy" @click="selectedIds = []">清空选择</button><button type="button" :disabled="busy || active || !current.enabled" @click="openImport">继续导入</button></div></div>
        </section>

        <section v-else-if="tab === 'runs'" class="panel collection-panel">
          <header class="collection-heading"><div><h2>执行记录</h2><p>查看发现、导入与同步的结果；失败项可单独重试。</p></div><button class="secondary" type="button" :disabled="busy" @click="action(() => loadRuns(runs.page))">刷新</button></header>
          <article v-for="r in runs.items" :key="r.id" class="run-row"><div><strong>{{ kindLabel(r.kind) }}</strong><p class="meta">{{ date(r.createdAt) }} / 处理 {{ r.processed }} / 失败 {{ r.failed }}</p><p v-if="r.lastError" class="error">{{ r.lastError }}</p></div><span class="badge">{{ statusLabel(r.status) }}</span><button class="secondary" type="button" :disabled="busy" @click="inspect(r)">查看明细</button></article>
          <p v-if="!runs.items.length" class="empty">尚无执行记录，可在仓库页开始发现或同步。</p>
          <nav v-if="runs.total > runs.size" class="pagination" aria-label="同步记录分页"><button class="secondary" :disabled="busy || runs.page === 0" @click="action(() => loadRuns(runs.page - 1))">上一页</button><span>第 {{ runs.page + 1 }} 页</span><button class="secondary" :disabled="busy || !runs.hasMore" @click="action(() => loadRuns(runs.page + 1))">下一页</button></nav>
        </section>

        <section v-else class="panel connection-settings">
          <header class="collection-heading"><div><h2>连接设置</h2><p>配置发现范围与同步策略，访问凭证始终隐藏。</p></div><button class="secondary" type="button" :disabled="busy" @click="edit(current)">编辑设置</button></header>
          <dl class="settings-facts"><div><dt>发现范围</dt><dd>{{ current.scope === 'OWNED' ? '本人拥有的仓库' : '本人及参与的组织 / 协作仓库' }}</dd></div><div><dt>Fork 仓库</dt><dd>{{ current.includeForks ? '包含' : '不包含' }}</dd></div><div><dt>归档仓库</dt><dd>{{ current.includeArchived ? '包含' : '不包含' }}</dd></div><div><dt>自动导入新仓库</dt><dd>{{ current.autoImport ? '已开启' : '已关闭' }}</dd></div><div><dt>自动同步</dt><dd>{{ schedule(current.scheduleMinutes) }}</dd></div><div><dt>访问凭证</dt><dd>已加密保存，不回显</dd></div></dl>
          <div class="actions"><button class="secondary" type="button" :disabled="busy" @click="validate(current)">验证连接</button><button class="secondary" type="button" :disabled="busy" @click="enable(current)">{{ current.enabled ? '暂停同步' : '启用同步' }}</button></div>
          <GithubWebhookPanel v-if="current.provider === 'GITHUB'" :workspace-id="workspaceId" :connection-id="current.id" />
          <section class="connection-danger"><h3>移除连接</h3><p>停止同步并删除凭证，已导入的项目与快照保留。</p><button class="danger" type="button" :disabled="busy" @click="remove(current)">移除连接</button></section>
        </section>
      </template>

      <AppDrawer v-model="opened" :title="editing ? '编辑连接' : '添加连接'" description="为当前工作空间配置代码托管平台的只读访问。" :busy="saving" :dirty="dirty" @closed="form.token = ''">
        <p v-if="formError" class="error form-error" role="alert">{{ formError }}</p>
        <form id="connection-form" @submit.prevent="save"><IntegrationConnectionFields :form="form" :editing="!!editing" :shared="isShared" @update:form="Object.assign(form, $event)" /></form>
        <template #footer="{ close }"><button type="button" class="secondary" :disabled="saving" @click="close">取消</button><button type="submit" form="connection-form" :disabled="saving">{{ saving ? '验证并保存中…' : '保存并验证' }}</button></template>
      </AppDrawer>
      <AppDrawer v-model="importOpen" title="导入仓库" :description="'将选中的 ' + selectedIds.length + ' 个仓库导入当前工作空间。'" :busy="importSaving">
        <p v-if="importError" class="error form-error" role="alert">{{ importError }}</p>
        <form id="repository-import-form" class="form-grid" @submit.prevent="imports">
          <label class="wide">导入方式<select v-model="targetProject"><option :value="null">为每个仓库分别新建项目</option><option v-for="p in projects" :key="p.id" :value="p.id">关联到已有项目：{{ p.name }}</option></select></label>
          <label v-if="targetProject === null" class="wide">新项目状态<select v-model="projectStatus"><option v-for="s in PROJECT_STATUSES" :key="s.value" :value="s.value">{{ s.label }}</option></select></label>
          <p class="form-hint wide">{{ targetProject === null ? '每个仓库分别创建项目，已导入的仓库不会重复创建。' : '所有选中的仓库将关联到同一个项目，项目的现有资料会保留。' }}</p>
          <label v-if="isShared" class="check-field wide"><input v-model="importConfirmed" type="checkbox" required /><span>确认所选仓库的数据对当前空间成员共享<small>共享范围包括本次选择的私有仓库。</small></span></label>
        </form>
        <template #footer="{ close }"><button class="secondary" type="button" :disabled="importSaving" @click="close">返回选择</button><button type="submit" form="repository-import-form" :disabled="importSaving || active">{{ importSaving ? '提交中…' : '确认导入' }}</button></template>
      </AppDrawer>
      <AppDrawer v-model="runOpen" :title="inspected ? kindLabel(inspected.kind) + '明细' : '执行明细'" :busy="working">
        <template v-if="inspected"><p>{{ statusLabel(inspected.status) }} / 处理 {{ inspected.processed }} / 失败 {{ inspected.failed }}</p><p v-if="inspected.lastError" class="error">{{ inspected.lastError }}</p></template>
        <p v-if="runError" class="error" role="alert">{{ runError }}</p>
        <output v-if="itemsLoading">正在读取明细…</output>
        <article v-for="i in runItems.items" :key="i.id" class="run-item"><strong>仓库 {{ i.repositoryId }}</strong><span class="badge">{{ statusLabel(i.status) }}</span><p v-if="i.lastError" class="error">{{ i.lastError }}</p><RouterLink v-if="i.projectId" :to="{ name: 'project-detail', params: { workspaceId, id: i.projectId } }">查看项目</RouterLink></article>
        <p v-if="!itemsLoading && !runItems.items.length" class="empty">当前执行没有逐项导入记录，发现和同步结果可在仓库及项目详情查看。</p>
        <nav v-if="runItems.total > runItems.size" class="pagination" aria-label="导入明细分页"><button class="secondary" :disabled="working || itemsLoading || runItems.page === 0" @click="loadItems(runItems.page - 1)">上一页</button><span>第 {{ runItems.page + 1 }} 页</span><button class="secondary" :disabled="working || itemsLoading || !runItems.hasMore" @click="loadItems(runItems.page + 1)">下一页</button></nav>
        <label v-if="isShared && hasRetryableItems" class="check-field"><input v-model="retryConfirmed" type="checkbox" /><span>确认重试导入的数据对当前空间成员共享</span></label>
        <template #footer="{ close }"><button class="secondary" type="button" :disabled="working" @click="close">关闭</button><button v-if="hasRetryableItems" type="button" :disabled="busy || active || !current?.enabled || (isShared && !retryConfirmed)" @click="retryFailed">重试本页失败项</button></template>
      </AppDrawer>
    </template>
  </section>
</template>

<script setup lang="ts">
import GithubWebhookPanel from '../components/GithubWebhookPanel.vue';
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import { integrationApi, projectApi, type IntegrationConnectionRequest, type IntegrationConnectionResponse,
  type IntegrationConnectionPageResponse, type RepositoryPageResponse, type IntegrationRunPageResponse,
  type IntegrationRunItemPageResponse, type IntegrationRunResponse, type ProjectStatus, type ProjectSummaryResponse,
  type WorkspaceResponse } from '../api/workstation';
import { workspace } from '../workspace';
import { PROJECT_STATUSES } from '../constants/options';
import AppDrawer from '../components/AppDrawer.vue';
import PageActions from '../components/PageActions.vue';
import IntegrationConnectionFields from '../components/IntegrationConnectionFields.vue';
import { useFormPanel } from '../composables/useFormPanel.mjs';

const route = useRoute(), router = useRouter();
const workspaceId = Number(route.params.workspaceId);
const isDetail = route.name === 'integration-detail';
const connectionId = Number(route.params.connectionId);
const canManage = computed(() => workspace.hasPermission('INTEGRATION_MANAGE'));
const isShared = computed(() => (workspace.state.current as WorkspaceResponse | null)?.type === 'SHARED');
const error = ref(''), notice = ref(''), loading = ref(true), working = ref(false);
const connections = reactive<IntegrationConnectionPageResponse>({ items: [], page: 0, size: 20, total: 0, hasMore: false });
const repositories = reactive<RepositoryPageResponse>({ items: [], page: 0, size: 20, total: 0, hasMore: false });
const runs = reactive<IntegrationRunPageResponse>({ items: [], page: 0, size: 20, total: 0, hasMore: false });
const runItems = reactive<IntegrationRunItemPageResponse>({ items: [], page: 0, size: 20, total: 0, hasMore: false });
const current = ref<IntegrationConnectionResponse | null>(null), editing = ref<IntegrationConnectionResponse | null>(null);
const inspected = ref<IntegrationRunResponse | null>(null);
const projects = ref<ProjectSummaryResponse[]>([]), selectedIds = ref<number[]>([]);
const active = ref(false), importOpen = ref(false), importSaving = ref(false), importError = ref('');
const runOpen = ref(false), runError = ref(''), retryConfirmed = ref(false), itemsLoading = ref(false);
const targetProject = ref<number | null>(null), projectStatus = ref<ProjectStatus>('PLANNING'), importConfirmed = ref(false);
const tab = ref('repositories');
const tabs = [{ value: 'repositories', label: '仓库' }, { value: 'runs', label: '执行记录' }, { value: 'settings', label: '连接设置' }];
function defaults(): IntegrationConnectionRequest {
  return { name: '', provider: 'GITHUB', baseUrl: 'https://github.com', token: '', scope: 'OWNED',
    includeForks: false, includeArchived: false, autoImport: false, scheduleMinutes: 0, expectedVersion: null, sharingConfirmed: false };
}
const form = reactive(defaults());
const { opened, saving, formError, dirty, openPanel, submit } = useFormPanel(form);
const busy = computed(() => working.value || saving.value || importSaving.value);
const selectableIds = computed(() => repositories.items.filter(r => !r.projectId).map(r => r.id));
const pageSelected = computed(() => selectableIds.value.length > 0 && selectableIds.value.every(id => selectedIds.value.includes(id)));
const pagePartiallySelected = computed(() => !pageSelected.value && selectableIds.value.some(id => selectedIds.value.includes(id)));
const hasRetryableItems = computed(() => inspected.value?.status === 'PARTIAL' && runItems.items.some(i => i.status === 'FAILED'));
watch(() => form.provider, p => { if (!editing.value) form.baseUrl = p === 'GITHUB' ? 'https://github.com' : 'https://gitlab.com'; });
const date = (v: string | null) => v ? new Date(v).toLocaleString() : '暂无';
const schedule = (v: number) => v ? '每 ' + v + ' 分钟自动同步' : '仅手动同步';
const providerLabel = (v: string) => v === 'GITHUB' ? 'GitHub' : 'GitLab';
const visibilityLabel = (v: string) => ({ PRIVATE: '私有', PUBLIC: '公开', INTERNAL: '内部', private: '私有', public: '公开', internal: '内部' }[v] || v);
const safeUrl = (v: string) => /^https?:\/\//i.test(v) ? v : '#';
const statusLabel = (s: string) => ({ QUEUED: '等待执行', RUNNING: '执行中', SUCCEEDED: '已完成', PARTIAL: '部分成功', FAILED: '失败', CANCELLED: '已取消' }[s] || s);
const kindLabel = (s: string) => ({ DISCOVER: '发现仓库', IMPORT: '导入仓库', SYNC: '同步开发情况', ISSUE_REFRESH: '刷新指定 Issue 来源' }[s] || s);
const detailRoute = (id: number) => ({ name: 'integration-detail', params: { workspaceId, connectionId: id } });
let disposed = false, polling = false, itemRequest = 0, repositoryRequest = 0, runRequest = 0;

async function action(task: () => Promise<unknown>, errorTarget = error) {
  if (busy.value) return;
  working.value = true; errorTarget.value = ''; notice.value = '';
  try { await task(); }
  catch (e) { if (!disposed) errorTarget.value = e instanceof Error ? e.message : '操作失败，请稍后重试'; }
  finally { working.value = false; }
}
async function loadConnections(page = 0): Promise<void> {
  const result = await integrationApi.list(workspaceId, page);
  if (!disposed && !result.items.length && result.total > 0 && page > 0) return loadConnections(page - 1);
  if (!disposed) Object.assign(connections, result);
}
async function findConnection(id: number, page = 0): Promise<IntegrationConnectionResponse | null> {
  if (disposed) return null;
  const result = await integrationApi.list(workspaceId, page, 100);
  if (disposed) return null;
  const found = result.items.find(c => c.id === id);
  if (found || !result.hasMore) return found || null;
  return findConnection(id, page + 1);
}
async function loadRepositories(page = 0) {
  const request = ++repositoryRequest;
  const result = await integrationApi.repositories(workspaceId, connectionId, page);
  if (!disposed && request === repositoryRequest) Object.assign(repositories, result);
}
async function loadRuns(page = 0) {
  const request = ++runRequest;
  const result = await integrationApi.runs(workspaceId, connectionId, page);
  const latest = page === 0 ? result : await integrationApi.runs(workspaceId, connectionId);
  if (disposed || request !== runRequest) return;
  Object.assign(runs, result);
  active.value = latest.items.some(r => ['QUEUED', 'RUNNING'].includes(r.status));
}
async function loadItems(page = 0) {
  if (!inspected.value) return;
  const id = inspected.value.id, request = ++itemRequest;
  runError.value = ''; itemsLoading.value = true;
  try {
    const result = await integrationApi.items(workspaceId, connectionId, id, page);
    if (!disposed && request === itemRequest) Object.assign(runItems, result);
  } catch (e) { if (!disposed && request === itemRequest) runError.value = e instanceof Error ? e.message : '读取明细失败'; }
  finally { if (request === itemRequest) itemsLoading.value = false; }
}
function newConnection() { editing.value = null; Object.assign(form, defaults()); openPanel(); }
function edit(c: IntegrationConnectionResponse) {
  editing.value = c;
  Object.assign(form, { name: c.name, provider: c.provider, baseUrl: c.baseUrl, token: '', scope: c.scope,
    includeForks: c.includeForks, includeArchived: c.includeArchived, autoImport: c.autoImport,
    scheduleMinutes: c.scheduleMinutes, expectedVersion: c.version, sharingConfirmed: false });
  openPanel();
}
async function save() {
  if (!canManage.value) return;
  const payload: IntegrationConnectionRequest = { ...form, token: form.token || null };
  form.token = '';
  let result: IntegrationConnectionResponse | null = null;
  const saved = await submit(async () => {
    result = editing.value
      ? await integrationApi.update(workspaceId, editing.value.id, payload)
      : await integrationApi.create(workspaceId, payload);
  });
  if (!saved || !result || disposed) return;
  const savedConnection = result as IntegrationConnectionResponse;
  if (isDetail) { current.value = savedConnection; await action(() => loadRuns()); notice.value = '连接已保存并验证。'; }
  else await router.push(detailRoute(savedConnection.id));
}
async function validate(c: IntegrationConnectionResponse) {
  await action(async () => {
    const updated = await integrationApi.validate(workspaceId, c.id);
    if (isDetail) current.value = updated; else await loadConnections(connections.page);
    notice.value = '连接验证通过；各类开发数据会在同步时分别检查权限。';
  });
}
async function enable(c: IntegrationConnectionResponse) {
  const confirmed = !isShared.value || c.enabled || window.confirm('启用后，所选范围的数据将对当前空间成员共享。是否继续？');
  if (!confirmed) return;
  await action(async () => {
    const updated = await integrationApi.enabled(workspaceId, c.id, { enabled: !c.enabled, expectedVersion: c.version, sharingConfirmed: confirmed });
    if (isDetail) { current.value = updated; await loadRuns(); }
    else await loadConnections(connections.page);
  });
}
async function remove(c: IntegrationConnectionResponse) {
  if (!window.confirm('移除连接将停止同步并删除凭证，已导入的项目和快照保留。是否继续？')) return;
  await action(async () => {
    await integrationApi.delete(workspaceId, c.id);
    if (isDetail) await router.push({ name: 'integrations', params: { workspaceId } });
    else await loadConnections(connections.page);
  });
}
async function trigger(kind: 'discover' | 'sync') {
  if (!current.value || active.value) return;
  await action(async () => {
    await integrationApi[kind](workspaceId, connectionId, crypto.randomUUID());
    await loadRuns();
    if (!active.value) await loadRepositories(repositories.page);
    notice.value = '任务已提交，可在执行记录中查看进度。';
  });
}
function togglePage() {
  if (pageSelected.value) selectedIds.value = selectedIds.value.filter(id => !selectableIds.value.includes(id));
  else selectedIds.value = [...new Set([...selectedIds.value, ...selectableIds.value])].slice(0, 100);
}
function openImport() { importError.value = ''; importConfirmed.value = false; importOpen.value = true; }
async function imports() {
  if (importSaving.value || !selectedIds.value.length || !current.value?.enabled || active.value) return;
  if (selectedIds.value.length > 100) { importError.value = '每次最多导入 100 个仓库。'; return; }
  if (isShared.value && !importConfirmed.value) { importError.value = '请确认工作空间共享范围。'; return; }
  importSaving.value = true; importError.value = '';
  try {
    await integrationApi.imports(workspaceId, connectionId, { requestKey: crypto.randomUUID(),
      items: selectedIds.value.map(repositoryId => ({ repositoryId, projectId: targetProject.value, status: projectStatus.value })),
      sharingConfirmed: importConfirmed.value });
    importOpen.value = false; selectedIds.value = []; tab.value = 'runs';
    try {
      await loadRuns();
      if (!active.value) { await loadRepositories(repositories.page); projects.value = await projectApi.listAll(workspaceId); }
      notice.value = '导入任务已提交。';
    }
    catch (e) { error.value = e instanceof Error ? e.message : '任务已提交，但读取进度失败，请刷新执行记录。'; }
  } catch (e) { importError.value = e instanceof Error ? e.message : '提交导入失败'; }
  finally { importSaving.value = false; }
}
async function inspect(r: IntegrationRunResponse) {
  inspected.value = r; retryConfirmed.value = false;
  Object.assign(runItems, { items: [], page: 0, total: 0, hasMore: false });
  runOpen.value = true;
  await loadItems();
}
async function retryFailed() {
  if (!hasRetryableItems.value || (isShared.value && !retryConfirmed.value)) return;
  await action(async () => {
    await integrationApi.imports(workspaceId, connectionId, { requestKey: crypto.randomUUID(),
      items: runItems.items.filter(i => i.status === 'FAILED').map(i => ({ repositoryId: i.repositoryId, projectId: i.projectId, status: i.projectStatus })),
      sharingConfirmed: retryConfirmed.value });
    runOpen.value = false; await loadRuns();
    notice.value = '已提交本页失败项的重试任务。';
  }, runError);
}
async function refreshInspectedRun() {
  if (!runOpen.value || !inspected.value) return;
  const inspectedId = inspected.value.id;
  const updated = await integrationApi.run(workspaceId, connectionId, inspectedId);
  if (disposed || !runOpen.value || inspected.value?.id !== inspectedId) return;
  inspected.value = updated;
  if (!itemsLoading.value) await loadItems(runItems.page);
}
const timer = setInterval(async () => {
  if (disposed || !isDetail || !current.value || busy.value || polling || document.hidden) return;
  polling = true;
  try {
    const wasActive = active.value;
    await loadRuns(runs.page);
    if (wasActive && !active.value) {
      await loadRepositories(repositories.page);
      current.value = await findConnection(connectionId);
      projects.value = await projectApi.listAll(workspaceId);
    }
    await refreshInspectedRun();
  } catch (e) { if (!disposed) error.value = e instanceof Error ? e.message : '更新同步进度失败'; }
  finally { polling = false; }
}, 3000);
onBeforeUnmount(() => { disposed = true; clearInterval(timer); form.token = ''; });
onMounted(async () => {
  try {
    if (!canManage.value) return;
    if (!isDetail) { await loadConnections(); return; }
    if (!Number.isSafeInteger(connectionId) || connectionId < 1) throw new Error('连接地址无效，请返回连接列表。');
    current.value = await findConnection(connectionId);
    if (disposed) return;
    if (!current.value) throw new Error('连接不存在或已移除，请返回连接列表。');
    const [, , availableProjects] = await Promise.all([loadRepositories(), loadRuns(), projectApi.listAll(workspaceId)]);
    if (!disposed) projects.value = availableProjects;
  } catch (e) { if (!disposed) error.value = e instanceof Error ? e.message : '读取数据源失败'; }
  finally { loading.value = false; }
});
</script>

<style scoped>
.integrations-page .panel { padding: 24px; }
.connection-table-heading, .connection-row { display: grid; grid-template-columns: minmax(0, 1fr) 90px 190px 125px; align-items: center; gap: 20px; padding: 20px 0; border-bottom: 1px solid var(--line); }
.connection-table-heading { color: var(--ink-soft); font-size: 12px; padding-block: 12px; }
.connection-row:last-child { border-bottom: 0; }
.connection-identity { display: grid; gap: 6px; min-width: 0; }
.connection-identity strong { color: var(--moss-dark); font-size: 15px; }
.connection-url { overflow-wrap: anywhere; }
.connection-timing { display: grid; gap: 6px; font-size: 12px; }
.connection-timing small { color: var(--ink-soft); }
.connection-row > .badge { justify-self: start; }
.connection-menu { position: relative; }
.connection-menu summary { cursor: pointer; padding: 8px; font-size: 12px; color: var(--ink-soft); }
.connection-menu-content { position: absolute; right: 0; top: 100%; z-index: 5; display: grid; width: 150px; padding: 6px; background: white; border: 1px solid var(--line); border-radius: 8px; box-shadow: var(--shadow); }
.connection-menu-content button { background: white; color: var(--ink); border: 0; text-align: left; box-shadow: none; }
.connection-menu-content button:hover { background: var(--canvas); }
.connection-menu-content .danger { color: var(--danger); }
.connection-overview { margin-bottom: 20px; }
.connection-overview .collection-heading p { overflow-wrap: anywhere; }
.connection-facts { display: flex; flex-wrap: wrap; gap: 10px 24px; color: var(--ink-soft); font-size: 12px; }
.integration-tabs { display: flex; gap: 24px; border-bottom: 1px solid var(--line); margin-bottom: 20px; }
.integration-tabs button { padding: 12px 2px; color: var(--ink-soft); background: transparent; box-shadow: none; border: 0; border-bottom: 2px solid transparent; border-radius: 0; }
.integration-tabs button.selected { color: var(--moss-dark); border-bottom-color: var(--moss); }
.repository-toolbar { display: flex; justify-content: space-between; gap: 16px; padding: 14px 0; border-bottom: 1px solid var(--line); }
.repository-row { display: grid; grid-template-columns: minmax(0, 1fr) auto; gap: 8px 24px; padding: 20px 0; border-bottom: 1px solid var(--line); }
.repository-select { grid-column: 1; }
.repository-description { grid-column: 1; padding-left: 27px; margin: 0; font-size: 13px; color: var(--ink-soft); overflow-wrap: anywhere; }
.repository-links { grid-column: 2; grid-row: 1 / 3; display: flex; align-items: center; gap: 16px; font-size: 12px; color: var(--moss-dark); }
.selection-bar { position: sticky; bottom: 16px; display: flex; justify-content: space-between; align-items: center; gap: 16px; padding: 14px 18px; margin-top: 20px; border: 1px solid #c2d2e4; border-radius: 10px; background: #edf3fa; box-shadow: 0 8px 24px #23374f15; font-size: 13px; }
.run-row { display: flex; align-items: center; gap: 18px; padding: 18px 0; border-bottom: 1px solid var(--line); }
.run-row > div { min-width: 0; flex: 1; }
.run-row p { margin-bottom: 0; overflow-wrap: anywhere; }
.run-item { border-bottom: 1px solid var(--line); padding: 18px 0; }
.run-item .badge { margin-left: 12px; }
.settings-facts { display: grid; gap: 0; margin: 0 0 24px; }
.settings-facts > div { display: grid; grid-template-columns: 150px minmax(0, 1fr); gap: 16px; padding: 16px 0; border-bottom: 1px solid var(--line); font-size: 13px; }
.settings-facts dt { color: var(--ink-soft); }
.settings-facts dd { margin: 0; overflow-wrap: anywhere; }
.connection-danger { margin-top: 32px; border-top: 1px solid var(--line); padding-top: 24px; }
.connection-danger h3 { font-size: 15px; }
.connection-danger p { font-size: 13px; color: var(--ink-soft); }
.integration-empty { padding: 48px 20px; text-align: center; }
.integration-empty h3 { margin: 0 0 10px; font-size: 17px; }
.integration-empty p { color: var(--ink-soft); font-size: 13px; line-height: 1.7; }
.loading-state { display: block; padding: 32px; color: var(--ink-soft); }
@media (max-width: 1100px) {
  .connection-table-heading, .connection-row { grid-template-columns: minmax(0, 1fr) 75px 140px; gap: 14px; }
  .connection-table-heading > :last-child { display: none; }
  .connection-row > .actions { grid-column: 1 / -1; }
}
@media (max-width: 620px) {
  .integrations-page .panel { padding: 18px; }
  .connection-table-heading { display: none; }
  .connection-row { grid-template-columns: minmax(0, 1fr) auto; }
  .connection-timing { grid-column: 1 / -1; }
  .repository-toolbar, .selection-bar { flex-wrap: wrap; }
  .repository-row { grid-template-columns: minmax(0, 1fr); }
  .repository-links { grid-column: 1; grid-row: auto; padding-left: 27px; }
  .selection-bar { bottom: 78px; }
  .run-row { flex-wrap: wrap; }
  .run-row > div { flex-basis: 100%; }
  .settings-facts > div { grid-template-columns: 110px minmax(0, 1fr); }
}
</style>
