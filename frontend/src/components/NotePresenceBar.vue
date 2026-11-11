<template>
  <aside v-if="state.enabled" class="note-presence" aria-label="笔记在线成员">
    <span class="presence-label">协作状态</span>
    <output v-if="!state.available" class="presence-unavailable">在线状态暂不可用，仍可正常保存</output>
    <template v-else>
      <span v-if="!others.length" class="presence-empty">当前没有其他在线成员</span>
      <span v-for="person in shown" :key="person.subjectId" class="presence-person" :data-state="person.state">
        <span class="presence-dot" aria-hidden="true"></span>
        {{ person.displayName || person.username || '未命名成员' }} · {{ person.state === 'EDITING' ? '正在编辑' : '正在查看' }}
      </span>
      <span v-if="others.length > shown.length">另有 {{ others.length - shown.length }} 人在线</span>
      <output v-if="editing" class="presence-warning">其他成员也在编辑，保存时仍会检查版本冲突</output>
    </template>
  </aside>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, watch } from 'vue';
import { notePresenceApi, subscribeNotePresence } from '../api/workstation';
import { auth } from '../auth';
import { workspace } from '../workspace';
import { createNotePresenceRuntime } from '../utils/notePresenceRuntime.mjs';

const props = defineProps({ workspaceId: { type: Number, required: true }, noteId: { type: Number, required: true },
  canWrite: { type: Boolean, default: false } });
const subject = auth.state.user?.subjectId;
const state = reactive({ enabled: false, available: false, participants: [] });
const others = computed(() => state.participants.filter(person => person.subjectId !== subject));
const shown = computed(() => others.value.slice(0, 6));
const editing = computed(() => others.value.some(person => person.state === 'EDITING'));
const runtime = createNotePresenceRuntime({ state,
  api: { get: () => notePresenceApi.get(props.workspaceId, props.noteId),
    update: (session, body) => notePresenceApi.update(props.workspaceId, props.noteId, session, body) },
  openStream: (refresh, disconnected) => subscribeNotePresence(props.workspaceId, props.noteId, refresh, disconnected) });
function visibility() { runtime.visibility(document.visibilityState === 'visible'); }
function focus() { runtime.focus(document.hasFocus()); }
function pagehide() { runtime.visibility(false); }
watch(() => props.canWrite, runtime.permission);
watch(() => [auth.state.user?.subjectId, workspace.state.current?.id], ([user, space]) => {
  if (user !== subject || space !== props.workspaceId) runtime.stop();
});
onMounted(() => {
  runtime.start({ canWrite: props.canWrite, active: document.visibilityState === 'visible', hasFocus: document.hasFocus() });
  document.addEventListener('visibilitychange', visibility);
  window.addEventListener('focus', focus);
  window.addEventListener('blur', focus);
  window.addEventListener('pagehide', pagehide);
  window.addEventListener('pageshow', visibility);
});
onBeforeUnmount(() => {
  runtime.stop();
  document.removeEventListener('visibilitychange', visibility);
  window.removeEventListener('focus', focus);
  window.removeEventListener('blur', focus);
  window.removeEventListener('pagehide', pagehide);
  window.removeEventListener('pageshow', visibility);
});
defineExpose({ activity: runtime.activity });
</script>

<style scoped>
.note-presence { display: flex; align-items: center; flex-wrap: wrap; gap: .5rem .85rem; padding: .6rem 18px; font-size: .82rem; color: var(--text-secondary, #64748b); }
.presence-label { font-weight: 600; }
.presence-person { display: inline-flex; align-items: center; gap: .35rem; overflow-wrap: anywhere; }
.presence-dot { width: .4rem; height: .4rem; border-radius: 50%; background: #94a3b8; flex-shrink: 0; }
.presence-person[data-state="EDITING"] .presence-dot { background: #178678; }
.presence-warning { flex-basis: 100%; color: #946415; }
.presence-unavailable { color: #946415; }
</style>
