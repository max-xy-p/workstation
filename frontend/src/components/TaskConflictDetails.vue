<template>
  <section aria-label="任务版本冲突" class="task-conflict">
    <h3>任务已变化，请核对后再决定</h3>
    <output class="task-conflict-notice">当前输入和操作意图已保留。请核对最新内容，再明确选择后续操作。</output>
    <p v-if="conflict.loading">正在读取最新任务…</p>
    <p v-if="conflict.message" class="error">{{ conflict.message }}</p>
    <template v-if="conflict.latest">
      <p>当前查看的服务端版本：{{ conflict.latest.version }}</p>
      <dl>
        <template v-for="row in rows" :key="row.field">
          <dt>{{ row.label }}</dt>
          <dd><span>当前输入：{{ row.local }}</span><span>服务端：{{ row.latest }}</span></dd>
        </template>
      </dl>
    </template>
    <slot />
  </section>
</template>
<script setup>
import { computed } from 'vue';
import { TASK_STATUSES, TASK_PRIORITIES, optionLabel } from '../constants/options';
import { taskComparisonRows } from '../utils/taskConflict.mjs';
const props = defineProps({ conflict: { type: Object, required: true }, draft: { type: Object, required: true } });
const rows = computed(() => taskComparisonRows(props.draft, props.conflict.latest, { status: value => optionLabel(TASK_STATUSES, value), priority: value => optionLabel(TASK_PRIORITIES, value) }));
</script>
<style scoped>
output { display: block; }
.task-conflict { border: 1px solid var(--border); padding: 1rem; border-radius: .75rem; margin-block-end: 1rem; overflow-wrap: anywhere; }
dt { font-weight: 600; margin-block-start: .65rem; }
dd { margin-inline-start: 0; }
dd span { display: block; white-space: pre-wrap; }
</style>
