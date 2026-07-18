<template>
  <div class="app-shell">
    <aside class="sidebar">
      <RouterLink class="brand" to="/">个人工作台</RouterLink>
      <nav class="nav">
        <RouterLink v-for="item in navItems" :key="item.name" :to="{ name: item.name }">
          {{ item.title }}
        </RouterLink>
      </nav>
    </aside>
    <main class="main">
      <header class="topbar">
        <h1>{{ route.meta.title }}</h1>
      </header>
      <RouterView />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { RouterLink, RouterView, useRoute } from 'vue-router';
import { routes } from './router';

const route = useRoute();
const navItems = computed(() => routes.filter((item) => ['dashboard', 'projects', 'tasks', 'notes', 'taxonomy'].includes(item.name))
  .map((item) => ({ name: item.name, title: item.meta.title })));
</script>
