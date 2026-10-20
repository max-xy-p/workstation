<template>
  <section class="task-issue-source" aria-label="任务 Issue 来源">
    <a :href="safeIssueUrl(source.url)" target="_blank" rel="noopener noreferrer">来源：{{ source.repositoryName }} #{{ source.issueNumber }}</a>
    <output v-if="difference" class="meta">{{ difference }}</output>
    <details :open="expanded">
      <summary>来源详情 · {{ source.status === 'OPEN' ? '开放' : '已关闭' }}</summary>
      <p>{{ source.title }}</p>
      <p class="meta">关闭原因：{{ sourceReasonLabel(source.stateReason) }}</p>
      <p class="meta">{{ sourceTrackingLabel(source.trackingStatus) }} · 上次成功：{{ date(source.lastSuccessAt) }}</p>
      <p class="meta">最近尝试：{{ date(source.lastAttemptAt) }} · 来源更新：{{ date(source.sourceUpdatedAt) }}</p>
      <output v-if="source.lastError" class="error">{{ source.lastError }}</output>
      <p class="meta">仅展示来源摘要，完整讨论请查看 GitHub。自动刷新取决于连接定时配置，管理员可从数据源手动同步。</p>
      <div v-if="canWrite" class="actions">
        <button v-if="source.status === 'CLOSED' && taskStatus !== 'DONE'" type="button" class="secondary" :disabled="busy" @click="$emit('complete')">将本地任务标记为完成</button>
        <button type="button" class="secondary" :disabled="busy" @click="unlink">解除来源关联</button>
      </div>
    </details>
  </section>
</template>
<script setup>
import { computed } from 'vue';
import { issueDifference, sourceTrackingLabel, safeIssueUrl, sourceReasonLabel } from '../utils/taskIssuePresentation.mjs';
const props = defineProps({ source: { type: Object, required: true }, taskStatus: String, canWrite: Boolean, busy: Boolean, expanded: Boolean });
const emit = defineEmits(['unlink', 'complete']);
const difference = computed(() => issueDifference(props.source, props.taskStatus));
const date = (value) => value ? new Date(value).toLocaleString() : '暂无';
function unlink() {
  if (!props.busy && window.confirm('解除 Issue 来源关联？本地任务和最后来源快照将保留。')) emit('unlink');
}
</script>
<style scoped>
.task-issue-source { border-block-start: 1px solid var(--border); padding-block: .65rem; margin-block: .5rem; overflow-wrap: anywhere; }
summary { cursor: pointer; margin-block: .5rem; }
.actions { flex-wrap: wrap; }
</style>
