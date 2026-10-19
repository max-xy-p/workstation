<template>
  <fieldset class="merge-choice" :data-choice="choiceKey">
    <legend>{{ label }}</legend>
    <details v-if="item.hasBase" class="merge-base"><summary>查看编辑基准</summary>
      <NoteRevisionText v-if="multiline" :content="item.base" label="编辑基准" /><p v-else>{{ format(item.base) }}</p>
    </details>
    <p v-else class="merge-base">没有可信的编辑基准，请核对两侧内容。</p>
    <div class="merge-sides">
      <section><h4>本地修改</h4><NoteRevisionText v-if="multiline" :content="item.local" label="本地原文" /><p v-else>{{ format(item.local) }}</p></section>
      <section><h4>服务端最新内容</h4><NoteRevisionText v-if="multiline" :content="item.remote" label="服务端原文" /><p v-else>{{ format(item.remote) }}</p></section>
    </div>
    <div class="merge-choice-actions" :aria-label="label + '的处理方式'">
      <button type="button" class="secondary" :aria-pressed="choice?.side === 'local'" :disabled="disabled" @click="emit('choose', { side: 'local' })">使用本地</button>
      <button type="button" class="secondary" :aria-pressed="choice?.side === 'remote'" :disabled="disabled" @click="emit('choose', { side: 'remote' })">使用服务端</button>
      <button v-if="allowManual" type="button" class="secondary" :aria-pressed="choice?.side === 'manual'" :disabled="disabled" @click="emit('choose', { side: 'manual', value: choice?.value ?? item.local })">手动编辑</button>
      <span v-if="choice" class="merge-resolved">已选择</span>
    </div>
    <label v-if="choice?.side === 'manual'" class="merge-manual">手动处理结果
      <textarea v-if="multiline" :value="choice.value" :disabled="disabled" rows="8" @input="changeManual" />
      <input v-else :value="choice.value" :disabled="disabled" maxlength="200" @input="changeManual" />
    </label>
  </fieldset>
</template>

<script setup>
import NoteRevisionText from './NoteRevisionText.vue';
defineProps({ item: { type: Object, required: true }, choice: Object, choiceKey: { type: String, required: true },
  label: { type: String, required: true }, multiline: Boolean, allowManual: Boolean, disabled: Boolean,
  format: { type: Function, default: value => value ?? '未设置' } });
const emit = defineEmits(['choose']);
function changeManual(event) { emit('choose', { side: 'manual', value: event.target.value }); }
</script>

<style scoped>
.merge-choice { min-width: 0; margin: 0 0 20px; padding: 18px; border: 1px solid #c99750; border-radius: 8px; }
.merge-choice legend { padding: 0 8px; font-size: 14px; font-weight: 600; }
.merge-base { font-size: 12px; color: var(--ink-soft); margin-bottom: 14px; }
.merge-base summary { cursor: pointer; margin-bottom: 10px; }
.merge-sides { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.merge-sides section { min-width: 0; }
.merge-sides h4 { margin: 0 0 10px; font-size: 13px; }
.merge-sides p, .merge-base p { white-space: pre-wrap; overflow-wrap: anywhere; }
.merge-choice-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 8px; margin-top: 16px; }
.merge-choice-actions button { font-size: 12px; }
.merge-choice-actions [aria-pressed="true"] { background: var(--moss-soft); border-color: var(--moss); color: var(--moss-dark); }
.merge-resolved { color: var(--moss-dark); font-size: 12px; }
.merge-manual { display: grid; gap: 10px; margin-top: 16px; font-size: 13px; }
.merge-manual textarea { font: 13px/1.8 Consolas, "Microsoft YaHei UI", monospace; }
@media (max-width: 680px) { .merge-sides { grid-template-columns: minmax(0, 1fr); } .merge-choice { padding: 14px; } }
</style>
