<template>
  <div class="workspace-switcher">
    <button ref="trigger" class="space-trigger" type="button" aria-haspopup="dialog" :aria-expanded="opened" aria-label="切换工作空间" @click="open">
      <span class="space-avatar"><Users v-if="current?.type === 'SHARED'" /><UserRound v-else /></span>
      <span class="space-trigger-copy"><strong>{{ current?.name || '选择工作空间' }}</strong><small>{{ workspaceTypeLabel(current?.type) }} · {{ workspaceRoleLabels[current?.role] || '成员' }}</small></span>
      <ChevronDown class="space-chevron" />
    </button>
    <Teleport to="body">
      <dialog ref="dialog" class="space-dialog" aria-labelledby="space-dialog-title" @close="closed" @cancel="cancel" @click="backdropClose">
        <div class="space-dialog-heading">
          <div><h2 id="space-dialog-title">{{ creating ? '新建共享空间' : '切换工作空间' }}</h2><p>{{ creating ? '创建后，你将成为这个空间的所有者。' : '选择你要继续工作的空间。' }}</p></div>
          <button type="button" class="space-icon-button" aria-label="关闭" :disabled="busy" @click="close"><X /></button>
        </div>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
        <form v-if="creating" class="space-create-form" @submit.prevent="create">
          <label>空间名称<input ref="createInput" v-model="newName" required maxlength="120" placeholder="例如：产品研发" :disabled="busy" /></label>
          <div class="space-footer-actions"><button class="secondary" type="button" :disabled="busy" @click="showList">返回列表</button><button type="submit" :disabled="busy || !newName.trim()">{{ busy ? '创建中…' : '创建并进入' }}</button></div>
        </form>
        <template v-else>
          <label class="space-search"><Search /><input ref="searchInput" v-model="keyword" aria-label="搜索工作空间" placeholder="搜索空间名称…" maxlength="120" :disabled="busy" @keydown="searchKeydown" /></label>
          <div class="space-list" :aria-busy="busy" @keydown="listKeydown">
            <section v-for="group in groups" :key="group.type" :aria-label="group.label">
              <h3>{{ group.label }}<span>{{ group.items.length }}</span></h3>
              <button v-for="item in group.items" :key="item.id" class="space-option" :class="{ selected: item.id === current?.id }" type="button" :disabled="busy" :aria-label="item.name + '，' + workspaceRoleLabels[item.role]" @click="select(item)">
                <span class="space-avatar"><Users v-if="item.type === 'SHARED'" /><UserRound v-else /></span>
                <span class="space-option-copy"><strong>{{ item.name }}</strong><small>{{ workspaceRoleLabels[item.role] }}</small></span>
                <Check v-if="item.id === current?.id" class="space-check" /><ChevronRight v-else class="space-chevron" />
              </button>
            </section>
            <p v-if="!groups.length" class="space-empty">没有匹配的空间，试试其他名称。</p>
          </div>
          <p v-if="busy" class="space-progress" role="status">正在切换工作空间…</p>
          <div class="space-dialog-footer">
            <button v-if="auth.state.user?.principalType === 'USER'" class="secondary" type="button" :disabled="busy" @click="showCreate"><Plus />新建空间</button>
            <button v-if="current" class="secondary" type="button" :disabled="busy" @click="settings"><Settings />空间设置</button>
          </div>
        </template>
      </dialog>
    </Teleport>
  </div>
</template>

<script setup>
import { Check, ChevronDown, ChevronRight, Plus, Search, Settings, UserRound, Users, X } from '@lucide/vue';
import { computed, nextTick, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { auth } from '../auth';
import { workspace } from '../workspace';
import { workspaceSwitchTarget } from '../utils/workspaceNavigation.mjs';
import { workspaceRoleLabels, workspaceTypeLabel } from '../utils/workspacePresentation';

const route = useRoute();
const router = useRouter();
const current = computed(() => workspace.state.current);
const trigger = ref(null);
const dialog = ref(null);
const searchInput = ref(null);
const createInput = ref(null);
const opened = ref(false);
const creating = ref(false);
const busy = ref(false);
const error = ref('');
const keyword = ref('');
const newName = ref('');
const groups = computed(() => {
  const query = keyword.value.trim().toLocaleLowerCase();
  const items = workspace.state.items.filter((item) => item.name.toLocaleLowerCase().includes(query));
  return [{ type: 'PERSONAL', label: '个人空间' }, { type: 'SHARED', label: '共享空间' }]
    .map((group) => ({ ...group, items: items.filter((item) => item.type === group.type) }))
    .filter((group) => group.items.length);
});

async function open() {
  creating.value = false;
  keyword.value = '';
  error.value = '';
  opened.value = true;
  dialog.value.showModal();
  await nextTick();
  searchInput.value?.focus();
}
function close() { dialog.value?.close(); }
function closed() { opened.value = false; trigger.value?.focus(); }
function cancel(event) { if (busy.value) event.preventDefault(); }
function backdropClose(event) {
  if (event.target !== dialog.value || busy.value) return;
  const bounds = dialog.value.getBoundingClientRect();
  if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) close();
}
async function showCreate() {
  creating.value = true;
  error.value = '';
  await nextTick();
  createInput.value?.focus();
}
async function showList() {
  creating.value = false;
  error.value = '';
  await nextTick();
  searchInput.value?.focus();
}
function optionButtons() { return [...dialog.value.querySelectorAll('.space-option:not(:disabled)')]; }
function searchKeydown(event) {
  if (!['ArrowDown', 'ArrowUp'].includes(event.key)) return;
  event.preventDefault();
  const buttons = optionButtons();
  (event.key === 'ArrowDown' ? buttons[0] : buttons.at(-1))?.focus();
}
function listKeydown(event) {
  if (!['ArrowDown', 'ArrowUp', 'Home', 'End'].includes(event.key)) return;
  const buttons = optionButtons();
  const index = buttons.indexOf(event.target);
  if (index < 0) return;
  event.preventDefault();
  const next = event.key === 'Home' ? 0 : event.key === 'End' ? buttons.length - 1
    : (index + (event.key === 'ArrowDown' ? 1 : -1) + buttons.length) % buttons.length;
  buttons[next]?.focus();
}
async function navigate(target) {
  busy.value = true;
  error.value = '';
  const previousId = current.value?.id;
  try {
    const failure = await router.push(target);
    if (!failure && workspace.currentId.value === target.params.workspaceId) close();
    else if (!failure) error.value = '这个空间暂时不可访问，请选择其他空间。';
  } catch (err) {
    if (Number(route.params.workspaceId) === previousId) workspace.selectWorkspace(previousId);
    error.value = err.message || '切换失败，请重试。';
  } finally { busy.value = false; }
}
function select(item) {
  if (item.id === current.value?.id) { close(); return; }
  return navigate(workspaceSwitchTarget(route.name, item.id));
}
function settings() {
  if (route.name === 'workspace-settings') { close(); return; }
  return navigate({ name: 'workspace-settings', params: { workspaceId: current.value.id } });
}
async function create() {
  if (busy.value || !newName.value.trim()) return;
  busy.value = true;
  error.value = '';
  try {
    const created = await workspace.createWorkspace(newName.value.trim());
    newName.value = '';
    await navigate({ name: 'workspace-settings', params: { workspaceId: created.id } });
  } catch (err) { error.value = err.message || '创建失败，请重试。'; }
  finally { busy.value = false; }
}
</script>

<style>
.space-dialog { width: min(460px, calc(100vw - 32px)); max-height: min(640px, calc(100dvh - 48px)); padding: 0; border: 1px solid var(--line); border-radius: 16px; color: var(--ink); background: white; box-shadow: 0 24px 90px #23374f30; }
.space-dialog::backdrop, .member-confirm-dialog::backdrop { background: #24344855; backdrop-filter: blur(3px); }
.space-dialog-heading { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; padding: 24px 24px 16px; }
.space-dialog-heading h2 { margin: 0; font-size: 20px; }
.space-dialog-heading p { margin: 6px 0 0; color: var(--muted); font-size: 13px; }
.space-icon-button { padding: 6px; min-height: 32px; color: var(--muted); background: transparent; border: 0; box-shadow: none; }
.space-icon-button svg { width: 18px; height: 18px; }
.space-search { display: flex; align-items: center; gap: 10px; margin: 0 24px 12px; padding: 0 12px; border: 1px solid var(--line); border-radius: 9px; }
.space-search:focus-within { outline: 2px solid var(--moss); outline-offset: 2px; }
.space-search svg { width: 18px; height: 18px; color: var(--muted); flex: none; }
.space-search input { min-width: 0; border: 0; background: transparent; padding: 12px 0; box-shadow: none; outline: none; }
.space-list { max-height: min(360px, calc(100dvh - 280px)); overflow-y: auto; padding: 0 12px 12px; }
.space-list h3 { display: flex; justify-content: space-between; margin: 16px 12px 8px; font-size: 12px; color: var(--muted); font-weight: 500; }
.space-option { display: flex; align-items: center; gap: 12px; width: 100%; text-align: left; margin: 2px 0; padding: 12px; border: 0; border-radius: 9px; background: transparent; color: var(--ink); box-shadow: none; }
.space-option:hover, .space-option:focus-visible { background: var(--canvas); }
.space-option.selected { background: #edf4f9; }
.space-avatar { display: grid; place-items: center; width: 36px; height: 36px; flex: none; background: #e9eff6; color: var(--moss); border-radius: 9px; }
.space-avatar svg { width: 18px; height: 18px; }
.space-option-copy { flex: 1; min-width: 0; }
.space-option-copy strong { display: block; overflow-wrap: anywhere; font-size: 14px; }
.space-option-copy small { display: block; margin-top: 3px; color: var(--muted); font-size: 12px; }
.space-check, .space-chevron { width: 16px; height: 16px; flex: none; color: var(--moss); }
.space-empty, .space-progress { text-align: center; padding: 18px; font-size: 13px; color: var(--muted); }
.space-dialog-footer { display: flex; gap: 10px; justify-content: space-between; padding: 16px 24px; border-top: 1px solid var(--line); }
.space-dialog-footer button { display: flex; align-items: center; gap: 6px; font-size: 12px; }
.space-dialog-footer svg { width: 16px; height: 16px; }
.space-dialog > .error { margin: 0 24px 12px; }
.space-create-form { display: grid; gap: 24px; padding: 4px 24px 24px; }
.space-create-form label { display: grid; gap: 8px; font-size: 13px; }
.space-footer-actions { display: flex; gap: 10px; justify-content: flex-end; }
</style>

<style scoped>
.space-trigger { display: flex; align-items: center; gap: 8px; width: 100%; padding: 10px; border: 1px solid var(--line); border-radius: 9px; color: var(--ink); background: #ffffffb3; box-shadow: none; text-align: left; }
.space-trigger:hover { border-color: var(--moss); background: #fff; }
.space-trigger-copy { flex: 1; min-width: 0; }
.space-trigger-copy strong { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 13px; }
.space-trigger-copy small { display: block; margin-top: 4px; color: var(--muted); font-size: 11px; }
</style>
