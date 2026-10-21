<template>
  <section class="task-issue-source" aria-label="任务 Issue 来源">
    <a :href="safeIssueUrl(source.url)" target="_blank" rel="noopener noreferrer">来源：{{ source.repositoryName }} #{{ source.issueNumber }}</a>
    <strong v-if="source.pending" class="pending-label">来源变化待处理</strong>
    <output v-if="difference" class="meta">{{ difference }}</output>
    <details :open="expanded">
      <summary>来源详情 · {{ source.status === 'OPEN' ? '开放' : '已关闭' }}</summary>
      <p>{{ source.title }}</p>
      <div v-if="source.pending && source.confirmedState" class="source-changes">
        <p class="meta">以下为上次确认基准与当前已知来源的差异，不包含期间所有事件。</p>
        <dl>
          <template v-for="change in changes" :key="change.field">
            <dt>{{ change.label }}</dt><dd><span>{{ change.before }}</span> → <span>{{ change.after }}</span></dd>
          </template>
        </dl>
      </div>
      <p v-if="source.confirmedAt" class="meta">最近确认：{{ date(source.confirmedAt) }} · {{ source.confirmedBy }}</p>
      <p class="meta">关闭原因：{{ sourceReasonLabel(source.stateReason) }}</p>
      <p class="meta">{{ sourceTrackingLabel(source.trackingStatus) }} · 上次成功：{{ date(source.lastSuccessAt) }}</p>
      <p class="meta">最近尝试：{{ date(source.lastAttemptAt) }} · 来源更新：{{ date(source.sourceUpdatedAt) }}</p>
      <output v-if="source.lastError" class="error">{{ source.lastError }}</output>
      <p class="meta">仅展示来源摘要，完整讨论请查看 GitHub。自动刷新取决于连接定时配置，管理员可从数据源手动同步。</p>
      <div v-if="canWrite" class="actions">
        <template v-if="source.pending">
          <button type="button" class="secondary" :disabled="busy" @click="$emit('confirm', 'KEEP')">保留任务并确认</button>
          <button v-if="source.trackingStatus === 'ACTIVE' && source.status === 'CLOSED' && taskStatus !== 'DONE'" type="button" :disabled="busy" @click="$emit('confirm', 'COMPLETE')">完成任务并确认</button>
          <button v-if="source.trackingStatus === 'ACTIVE' && source.status === 'OPEN' && taskStatus === 'DONE'" type="button" :disabled="busy" @click="$emit('confirm', 'REOPEN')">重新开启并确认</button>
        </template>
        <button type="button" class="secondary" :disabled="busy" @click="unlink">解除来源关联</button>
      </div>
    </details>
  </section>
</template>
<script setup>
import { computed } from 'vue';
import { issueDifference, sourceTrackingLabel, safeIssueUrl, sourceReasonLabel, sourceChanges } from '../utils/taskIssuePresentation.mjs';
const props = defineProps({ source: { type: Object, required: true }, taskStatus: String, canWrite: Boolean, busy: Boolean, expanded: Boolean });
const emit = defineEmits(['unlink', 'confirm']);
const changes = computed(() => sourceChanges(props.source));
const difference = computed(() => issueDifference(props.source, props.taskStatus));
const date = (value) => value ? new Date(value).toLocaleString() : '暂无';
function unlink() {
  if (!props.busy && window.confirm('解除 Issue 来源关联？本地任务和最后来源快照将保留。')) emit('unlink');
}
</script>
<style scoped>
.task-issue-source { border-block-start: 1px solid var(--border); padding-block: .65rem; margin-block: .5rem; overflow-wrap: anywhere; }
.pending-label { display: block; color: var(--accent); margin-block: .5rem; }
dd { margin-inline-start: 0; margin-block-end: .6rem; white-space: pre-wrap; }
summary { cursor: pointer; margin-block: .5rem; }
.actions { flex-wrap: wrap; }
</style>
