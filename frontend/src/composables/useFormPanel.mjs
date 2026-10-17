import { computed, ref } from 'vue';

// 表单按需打开；保存失败保留输入，成功后关闭。
export function useFormPanel(form) {
  const opened = ref(false);
  const saving = ref(false);
  const formError = ref('');
  const initial = ref('');
  const dirty = computed(() => opened.value && JSON.stringify(form) !== initial.value);
  function openPanel() { initial.value = JSON.stringify(form); formError.value = ''; opened.value = true; }
  async function submit(task) {
    if (saving.value) return false;
    saving.value = true;
    formError.value = '';
    try { await task(); opened.value = false; return true; }
    catch (error) { formError.value = error instanceof Error ? error.message : '保存失败，请稍后重试'; return false; }
    finally { saving.value = false; }
  }
  return { opened, saving, formError, dirty, openPanel, submit };
}
