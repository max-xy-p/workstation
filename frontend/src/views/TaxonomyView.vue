<template>
  <section class="page taxonomy-page">
    <p v-if="error" class="error">{{ error }}</p>
    <section class="panel taxonomy-panel">
      <div class="section-heading">
        <div><span class="eyebrow">交叉检索</span><h2>标签</h2></div>
        <span class="section-count">{{ tags.length }} 个</span>
      </div>
      <form class="form-grid" @submit.prevent="saveTag">
        <label class="wide">名称<input v-model="tagForm.name" required /></label>
        <div class="actions wide">
          <button type="submit">{{ tagForm.id ? '保存标签' : '创建标签' }}</button>
          <button type="button" class="secondary" @click="resetTag">清空</button>
        </div>
      </form>
      <div v-if="tags.length" class="list">
        <article v-for="tag in tags" :key="tag.id" class="card taxonomy-card tag-card">
          <div class="card-title"><h3>{{ tag.name }}</h3></div>
          <div class="actions">
            <button class="secondary" @click="editTag(tag)">编辑</button>
            <button class="danger" @click="removeTag(tag.id)">删除</button>
          </div>
        </article>
      </div>
      <p v-else class="empty">暂无标签</p>
    </section>
  </section>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { tagApi } from '../api/workstation';
import { workspaceIdFromRoute } from '../utils/workspaceRoute';

const route = useRoute();
const workspaceId = computed(() => workspaceIdFromRoute(route));
const error = ref('');
const tags = ref([]);
const tagForm = reactive({ id: null, name: '' });

function resetTag() {
  Object.assign(tagForm, { id: null, name: '' });
}

function editTag(tag) {
  Object.assign(tagForm, { id: tag.id, name: tag.name });
}

async function load() {
  tags.value = await tagApi.list(workspaceId.value);
}

async function saveTag() {
  try {
    error.value = '';
    const payload = { name: tagForm.name };
    if (tagForm.id) await tagApi.update(workspaceId.value, tagForm.id, payload);
    else await tagApi.create(workspaceId.value, payload);
    resetTag();
    await load();
  } catch (err) {
    error.value = err.message;
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
