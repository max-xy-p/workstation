<template>
  <AppDrawer v-model="opened" title="将 Issue 纳入任务" :busy="saving || loading" :dirty="dirty" @update:model-value="closed">
    <p>来源：{{ candidate.title }}</p>
    <p class="meta">{{ candidate.status === 'OPEN' ? '开放' : '已关闭' }} · 来源正文与讨论请查看 GitHub。同步只更新来源信息，不覆盖本地任务。</p>
    <p v-if="formError" class="error" role="alert">{{ formError }}</p>
    <output v-if="loading">正在读取任务和依据笔记…</output>
    <form id="issue-task-form" class="form-grid" @submit.prevent="save">
      <label class="wide">处理方式<select v-model="form.mode" :disabled="loading || saving"><option value="create">创建新任务</option><option value="link">关联已有任务</option></select></label>
      <template v-if="form.mode === 'create'">
        <label class="wide">本地标题<input v-model="form.title" required maxlength="200" :disabled="saving" /></label>
        <p v-if="candidate.title.length > 200" class="meta wide">来源标题超过 200 字，请调整本地标题后提交。</p>
        <label>状态<select v-model="form.status" required :disabled="saving"><option v-for="s in TASK_STATUSES" :key="s.value" :value="s.value">{{ s.label }}</option></select></label>
        <label>优先级<select v-model="form.priority" required :disabled="saving"><option v-for="p in TASK_PRIORITIES" :key="p.value" :value="p.value">{{ p.label }}</option></select></label>
        <label class="wide">截止日期<input v-model="form.dueDate" type="date" :disabled="saving" /></label>
        <label class="wide">依据笔记<select v-model="form.evidenceNoteId" :disabled="saving"><option value="">未关联</option><option v-for="n in notes" :key="n.id" :value="n.id">{{ n.title }}</option></select></label>
        <label class="wide">本地描述<textarea v-model="form.description" :disabled="saving" /></label>
      </template>
      <label v-else class="wide">当前项目未关联来源的任务<select v-model="form.taskId" required :disabled="loading || saving"><option value="" disabled>请选择任务</option><option v-for="t in eligibleTasks" :key="t.id" :value="t.id">{{ t.title }}</option></select></label>
      <p v-if="form.mode === 'link' && !loading && !eligibleTasks.length" class="meta wide">当前没有可关联任务，可切换为创建新任务。</p>
      <label class="wide"><span><input v-model="form.sharingConfirmed" type="checkbox" required :disabled="saving" /> 我确认来源快照将按当前 Workspace 权限对成员可见，包括私有仓库信息。</span></label>
    </form>
    <template #footer="{ close }"><button type="button" class="secondary" :disabled="saving || loading" @click="close">取消</button><button form="issue-task-form" type="submit" :disabled="saving || loading">{{ saving ? '提交中…' : '确认' }}</button></template>
  </AppDrawer>
</template>
<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { issueApi, noteApi, taskApi } from '../api/workstation';
import { TASK_STATUSES, TASK_PRIORITIES } from '../constants/options';
import AppDrawer from './AppDrawer.vue';
import { useFormPanel } from '../composables/useFormPanel.mjs';
const props = defineProps({ workspaceId: Number, projectId: Number, candidate: { type: Object, required: true } });
const emit = defineEmits(['close', 'saved']);
const form = reactive({ mode: 'create', title: props.candidate.title, description: '', status: 'TODO', priority: 'MEDIUM', dueDate: '', evidenceNoteId: '', taskId: '', sharingConfirmed: false });
const { opened, saving, formError, dirty, openPanel, submit } = useFormPanel(form);
const loading = ref(true);
const tasks = ref([]);
const sources = ref([]);
const notes = ref([]);
const eligibleTasks = computed(() => tasks.value.filter((task) => !sources.value.some((source) => source.taskId === task.id)));
function closed(value) { if (!value) emit('close'); }
onMounted(async () => {
  openPanel();
  try {
    [tasks.value, sources.value, notes.value] = await Promise.all([
      taskApi.listAll(props.workspaceId, { projectId: props.projectId }),
      issueApi.listAll(props.workspaceId, props.projectId),
      noteApi.listAll(props.workspaceId, { projectId: props.projectId })
    ]);
  } catch (error) { formError.value = error.message; }
  finally { loading.value = false; }
});
async function save() {
  if (loading.value) return;
  let result;
  const saved = await submit(async () => {
    const common = { developmentItemId: props.candidate.id, sharingConfirmed: form.sharingConfirmed };
    if (form.mode === 'link') result = await issueApi.link(props.workspaceId, props.projectId, { ...common, taskId: Number(form.taskId) });
    else result = await issueApi.create(props.workspaceId, props.projectId, { ...common, task: {
      title: form.title, description: form.description || null, projectId: props.projectId,
      status: form.status, priority: form.priority, dueDate: form.dueDate || null,
      evidenceNoteId: form.evidenceNoteId ? Number(form.evidenceNoteId) : null
    } });
  });
  if (saved) emit('saved', result.task);
}
</script>
