<template>
  <span><template v-for="(part, index) in parts" :key="index"><mark v-if="part.matched">{{ part.text }}</mark><template v-else>{{ part.text }}</template></template></span>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({ text: { type: String, default: '' }, keyword: { type: String, default: '' } });
const parts = computed(() => {
  const keyword = props.keyword.trim().toLowerCase();
  if (!keyword) return [{ text: props.text, matched: false }];
  const result = [];
  const source = props.text.toLowerCase();
  let start = 0;
  let found = source.indexOf(keyword);
  while (found !== -1) {
    if (found > start) result.push({ text: props.text.slice(start, found), matched: false });
    result.push({ text: props.text.slice(found, found + keyword.length), matched: true });
    start = found + keyword.length;
    found = source.indexOf(keyword, start);
  }
  if (start < props.text.length) result.push({ text: props.text.slice(start), matched: false });
  return result;
});
</script>

<style scoped>
mark { background: #f7e7ad; color: inherit; padding: 0 0.08em; border-radius: 0.15em; }
</style>
