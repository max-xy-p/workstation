<template>
  <Teleport to="body">
    <dialog ref="dialog" class="app-drawer" :aria-label="title" :aria-busy="busy" @cancel.prevent="requestClose" @click="backdropClose" @close="closed">
      <header class="drawer-heading">
        <div><h2>{{ title }}</h2><p v-if="description">{{ description }}</p></div>
        <button class="drawer-close secondary" type="button" aria-label="关闭面板" :disabled="busy" @click="requestClose"><X :size="20" /></button>
      </header>
      <div class="drawer-body"><slot /></div>
      <footer v-if="$slots.footer" class="drawer-footer"><slot name="footer" :close="requestClose" /></footer>
    </dialog>
  </Teleport>
</template>

<script setup>
import { nextTick, onBeforeUnmount, ref, watch } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import { X } from '@lucide/vue';

const props = defineProps({ modelValue: Boolean, title: { type: String, required: true }, description: { type: String, default: '' }, busy: Boolean, dirty: Boolean });
const emit = defineEmits(['update:modelValue', 'closed']);
const dialog = ref(null);
function canClose() {
  return !props.busy && (!props.dirty || window.confirm('还有未保存的修改，确认放弃并关闭？'));
}
function requestClose() {
  if (canClose()) emit('update:modelValue', false);
}
function backdropClose(event) {
  if (event.target !== dialog.value) return;
  const bounds = dialog.value.getBoundingClientRect();
  if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) requestClose();
}
function closed() { if (!props.modelValue) emit('closed'); }
function guardNavigation() {
  if (!props.modelValue) return true;
  if (!canClose()) return false;
  emit('update:modelValue', false);
  return true;
}
watch(() => props.modelValue, async (opened) => {
  await nextTick();
  if (!dialog.value || props.modelValue !== opened) return;
  if (opened && !dialog.value.open) {
    dialog.value.showModal();
    dialog.value.querySelector('input:not([type="checkbox"]):not([type="hidden"]), textarea, select')?.focus();
  }
  else if (!opened && dialog.value.open) dialog.value.close();
}, { immediate: true });
onBeforeRouteLeave(guardNavigation);
onBeforeRouteUpdate(guardNavigation);
onBeforeUnmount(() => dialog.value?.close());
</script>
