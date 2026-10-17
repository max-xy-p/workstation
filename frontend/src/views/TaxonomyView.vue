<template>
  <section class="page taxonomy-page">
    <PageActions><button v-if="canWrite" type="button" @click="createTag">新建标签</button></PageActions>
    <p v-if="error" class="error">{{ error }}</p>
    <section class="panel taxonomy-panel">
      <div class="section-heading">
        <div><span class="eyebrow">交叉检索</span><h2>标签</h2></div>
        <span class="section-count">{{ tags.length }} 个</span>
      </div>
      <div v-if="tags.length" class="entity-collection">
        <article v-for="tag in tags" :key="tag.id" class="card taxonomy-card tag-card">
          <div class="card-title"><h3>{{ tag.name }}</h3></div>
          <div v-if="canWrite" class="actions">
            <button class="secondary" @click="editTag(tag)">编辑</button>
            <button class="danger" @click="removeTag(tag.id)">删除</button>
          </div>
        </article>
      </div>
      <p v-else class="empty">暂无标签</p>
    </section>
    <AppDrawer v-model="opened" :title="tagForm.id ? '编辑标签' : '新建标签'" :busy="saving" :dirty="dirty">
      <p v-if="formError" class="error form-error" role="alert">{{ formError }}</p>
      <form id="tag-form" class="form-grid" @submit.prevent="saveTag"><label class="wide">名称<input v-model="tagForm.name" required maxlength="80" /></label></form>
      <template #footer="{ close }"><button type="button" class="secondary" :disabled="saving" @click="close">取消</button><button type="submit" form="tag-form" :disabled="saving">{{ saving ? '保存中…' : '保存标签' }}</button></template>
    </AppDrawer>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { tagApi } from '../api/workstation';
import { workspaceIdFromRoute } from '../utils/workspaceRoute';
import { workspace } from '../workspace';
import AppDrawer from '../components/AppDrawer.vue';
import PageActions from '../components/PageActions.vue';
import { useFormPanel } from '../composables/useFormPanel.mjs';

const route = useRoute();
const workspaceId = computed(() => workspaceIdFromRoute(route));
const error = ref('');
const tags = ref([]);
const tagForm = reactive({ id: null, name: '' });
const canWrite = computed(() => workspace.hasPermission('TAG_WRITE'));
const { opened, saving, formError, dirty, openPanel, submit } = useFormPanel(tagForm);
function createTag() { resetTag(); openPanel(); }

function resetTag() {
  Object.assign(tagForm, { id: null, name: '' });
}

function editTag(tag) {
  Object.assign(tagForm, { id: tag.id, name: tag.name });
  openPanel();
}

async function load() {
  tags.value = await tagApi.list(workspaceId.value);
}

async function saveTag() {
  if (!canWrite.value) return;
  const saved = await submit(async () => {
    const payload = { name: tagForm.name };
    if (tagForm.id) await tagApi.update(workspaceId.value, tagForm.id, payload);
    else await tagApi.create(workspaceId.value, payload);
  });
  if (saved) {
    resetTag();
    try { await load(); } catch (err) { error.value = err.message; }
  }
}

async function removeTag(id) {
  if (!window.confirm('确认删除标签？')) return;
  try {
    error.value = '';
    await tagApi.delete(workspaceId.value, id);
    await load();
  } catch (err) {
    error.value = err.message;
  }
}

onMounted(async () => {
  try {
    await load();
  } catch (err) {
    error.value = err.message;
  }
});
</script>
