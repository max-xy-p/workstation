<template>
  <section class="revision-text" :aria-label="label">
    <header><strong>{{ label }}</strong><span>{{ content.length.toLocaleString() }} 字符</span></header>
    <pre>{{ textWindow(content, page) }}</pre>
    <nav v-if="pages > 1" aria-label="原文分段">
      <button type="button" class="secondary" :disabled="page === 0" @click="page--">上一段</button>
      <span>{{ page + 1 }} / {{ pages }}</span>
      <button type="button" class="secondary" :disabled="page + 1 >= pages" @click="page++">下一段</button>
    </nav>
  </section>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { textWindow } from '../utils/noteDiffLimits.mjs';
const props = defineProps<{ content: string; label: string }>();
const page = ref(0);
const pages = computed(() => Math.max(1, Math.ceil(props.content.length / 12_000)));
watch(() => props.content, () => { page.value = 0; });
</script>

<style scoped>
.revision-text { min-width: 0; border: 1px solid var(--line); border-radius: 8px; overflow: hidden; }
.revision-text header, .revision-text nav { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 16px; background: var(--canvas); font-size: 12px; }
.revision-text header span, .revision-text nav span { color: var(--ink-soft); }
.revision-text pre { margin: 0; padding: 18px; max-height: 55dvh; overflow: auto; white-space: pre-wrap; overflow-wrap: anywhere; font: 13px/1.8 Consolas, "Microsoft YaHei UI", monospace; }
.revision-text nav { border-top: 1px solid var(--line); }
</style>
