<template>
  <section class="empty-state">
    <h1>工作空间不可用</h1>
    <p>当前账号没有可访问的工作空间，或该工作空间已经移除。</p>
    <button class="button" type="button" @click="signOut">退出登录</button>
  </section>
</template>

<script setup>
import { useRouter } from 'vue-router';
import { auth } from '../auth';

const router = useRouter();

async function signOut() {
  await auth.logout();
  await router.replace({ name: 'login' });
}
</script>
