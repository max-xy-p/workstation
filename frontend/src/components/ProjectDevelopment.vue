<template>
  <section class="development-panel">
    <div class="card-title"><h3>仓库与开发情况</h3><RouterLink v-if="canManage" :to="{ name: 'integrations', params: { workspaceId } }">管理数据源</RouterLink></div>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <p v-if="!summary.repositories.length" class="empty">尚未关联仓库，可从数据源导入或关联到此项目。</p>
    <article v-for="r in summary.repositories" :key="r.id" class="card">
      <a :href="safeUrl(r.url)" target="_blank" rel="noopener noreferrer">{{ r.provider }} · {{ r.namespace }}/{{ r.name }}</a>
      <p class="meta">{{ r.visibility }} · {{ r.defaultBranch || '无默认分支' }} · {{ r.language || '语言未提供' }} · {{ r.archived ? '已归档' : '未归档' }}</p>
      <p class="meta">资料同步：{{ date(r.lastSyncedAt) }} · 最近推送：{{ date(r.lastPushedAt) }}{{ r.connectionId ? '' : ' · 数据源已断开' }}</p>
      <p class="meta">默认分支最近流水线：{{ r.latestPipelineStatus ? stateLabel(r.latestPipelineStatus) : '未采集或无运行' }}</p>
      <p v-if="r.lastError" class="error">{{ r.lastError }}</p>
      <div v-for="s in summary.snapshots.filter(s => s.repositoryId === r.id)" :key="s.kind" class="meta">
        {{ labels[s.kind] }}：
        <span v-if="s.available">自 {{ date(s.since) }} 起采集 {{ s.collected }} 条{{ s.truncated ? '（已达到上限，结果不完整）' : '' }}{{ ['ISSUE', 'CHANGE_REQUEST'].includes(s.kind) ? '，其中开放 ' + s.openCount + ' 条' : '' }}</span>
        <span v-else>未能采集 · {{ s.message }}；列表可能保留旧快照</span>
        · {{ date(s.syncedAt) }}
      </div>
      <button v-if="canWrite" class="secondary" @click="unbind(r.id)">解除项目关联</button>
    </article>
    <template v-if="summary.repositories.length">
      <div class="toolbar"><label>开发记录<select v-model="kind" @change="loadItems(0)"><option v-for="(label, value) in labels" :key="value" :value="value">{{ label }}</option></select></label><button class="secondary" @click="load">刷新快照</button></div>
      <p class="meta">提交与流水线来自默认分支；记录受采集时间和上限限制，不表示全部历史或项目完成进度。</p>
      <article v-for="i in items.items" :key="i.id" class="development-row"><a v-if="i.url" :href="safeUrl(i.url)" target="_blank" rel="noopener noreferrer">{{ i.title }}</a><span v-else>{{ i.title }}</span>
        <span class="badge">{{ stateLabel(i.status) }}</span><p class="meta">{{ i.author || '' }} · {{ i.branch || '' }} · {{ date(i.sourceUpdatedAt) }}</p>
        <template v-if="i.kind === 'ISSUE' && isGithub(i.repositoryId)">
          <RouterLink v-if="i.linkedTaskId" :to="{ name: 'tasks', params: { workspaceId }, query: { projectId, taskId: i.linkedTaskId } }">查看关联任务</RouterLink>
          <button v-else-if="canWriteTask" type="button" class="secondary" @click="selectedIssue = i">创建任务 / 关联已有任务</button>
        </template>
      </article>
      <p v-if="!items.items.length" class="empty">当前没有已采集记录，请查看仓库同步状态。</p>
      <nav class="pagination" aria-label="开发记录分页"><button class="secondary" :disabled="items.page === 0" @click="loadItems(items.page - 1)">上一页</button><span>已保存 {{ items.total }} 条</span><button class="secondary" :disabled="!items.hasMore" @click="loadItems(items.page + 1)">下一页</button></nav>
    </template>
    <IssueTaskDialog v-if="selectedIssue" :workspace-id="workspaceId" :project-id="projectId" :candidate="selectedIssue" @close="selectedIssue = null" @saved="taskSaved" />
  </section>
</template>
<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { RouterLink, useRouter } from 'vue-router';
import IssueTaskDialog from './IssueTaskDialog.vue';
import { integrationApi, type ProjectDevelopmentResponse, type DevelopmentKind, type DevelopmentItemPageResponse } from '../api/workstation';
import { workspace } from '../workspace';
const props = defineProps<{ workspaceId: number; projectId: number }>();
const error = ref('');
const router = useRouter();
const selectedIssue = ref<import('../api/workstation').DevelopmentItemResponse | null>(null);
const canWriteTask = computed(() => workspace.hasPermission('TASK_WRITE'));
const isGithub = (id: number) => summary.repositories.some((repo) => repo.id === id && repo.provider === 'GITHUB');
async function taskSaved(task: { id: number }) { selectedIssue.value = null; await router.push({ name: 'tasks', params: { workspaceId: props.workspaceId }, query: { projectId: props.projectId, taskId: task.id } }); }
const summary = reactive<ProjectDevelopmentResponse>({ repositories: [], snapshots: [] });
const items = reactive<DevelopmentItemPageResponse>({ items: [], page: 0, size: 20, total: 0, hasMore: false });
const kind = ref<DevelopmentKind>('ISSUE');
const labels: Record<DevelopmentKind, string> = { ISSUE: 'Issue', CHANGE_REQUEST: 'PR / MR', COMMIT: '提交', PIPELINE: '流水线' };
const date = (v: string | null) => v ? new Date(v).toLocaleString() : '暂无';
const safeUrl = (v: string) => /^https?:\/\//i.test(v) ? v : '#';
const stateLabel = (s: string) => ({ OPEN: '开放', MERGED: '已合并', CLOSED: '已关闭', SUCCESS: '成功', SKIPPED: '已跳过', NEUTRAL: '中性结果', FAILED: '失败', CANCELLED: '取消', RUNNING: '运行中', PENDING: '等待', COMMITTED: '已提交', UNKNOWN: '未知' }[s] || s);
const canManage = computed(() => workspace.hasPermission('INTEGRATION_MANAGE'));
const canWrite = computed(() => workspace.hasPermission('PROJECT_WRITE'));
async function loadItems(page = 0) { try { Object.assign(items, await integrationApi.developmentItems(props.workspaceId, props.projectId, kind.value, page)); } catch (e) { error.value = e instanceof Error ? e.message : '读取开发记录失败'; } }
async function load() { try { error.value = ''; Object.assign(summary, await integrationApi.development(props.workspaceId, props.projectId)); await loadItems(); } catch (e) { error.value = e instanceof Error ? e.message : '读取开发情况失败'; } }
async function unbind(id: number) {
  if (!window.confirm('解除此项目的仓库关联？仓库快照将保留。')) return;
  try { await integrationApi.unbind(props.workspaceId, props.projectId, id); await load(); } catch (e) { error.value = e instanceof Error ? e.message : '解除关联失败'; }
}
onMounted(load);
</script>
<style scoped>
.development-panel { border-block: 1px solid var(--border); margin-block: 1rem; padding-block: 1rem; }
.development-row { padding-block: .75rem; border-bottom: 1px solid var(--border); }
.development-row .badge { margin-left: .75rem; }
</style>
