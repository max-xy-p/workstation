<template>
  <main class="login-page">
    <section class="login-atmosphere" aria-labelledby="login-title">
      <div class="login-brand">
        <span class="brand-mark">知</span>
        <span>
          <strong>个人工作台</strong>
          <small>Knowledge desk</small>
        </span>
      </div>

      <div class="login-statement">
        <span class="eyebrow">Private knowledge base</span>
        <h1 id="login-title">回到你的<br>知识书房。</h1>
        <p>笔记、项目与仍在生长的想法，都安静地留在这里。</p>
      </div>

      <div class="login-index" aria-hidden="true">
        <span>OWNER / 01</span>
        <strong>知</strong>
      </div>
    </section>

    <section class="login-entry" aria-label="登录">
      <form class="login-form" @submit.prevent="submit">
        <div class="login-form-heading">
          <span class="section-kicker">Welcome back</span>
          <h2>验证身份</h2>
          <p>这是一个私人空间，请使用站点所有者账号进入。</p>
        </div>

        <p v-if="expired" class="login-notice" role="status">登录状态已失效，请重新验证。</p>
        <p v-if="error" class="error" role="alert">{{ error }}</p>

        <label class="login-field">
          <span>用户名</span>
          <input
            v-model.trim="form.username"
            name="username"
            type="text"
            autocomplete="username"
            autofocus
            required
            placeholder="请输入用户名"
          >
        </label>

        <label class="login-field">
          <span>密码</span>
          <input
            v-model="form.password"
            name="password"
            type="password"
            autocomplete="current-password"
            required
            placeholder="请输入密码"
          >
        </label>

        <button class="login-submit" type="submit" :disabled="submitting">
          <span>{{ submitting ? '正在验证…' : '进入工作台' }}</span>
          <AppIcon v-if="!submitting" name="arrow" />
        </button>

        <p class="login-footnote">
          <span class="status-dot"></span>
          密码由服务器校验，浏览器不会保存明文凭据
        </p>
      </form>
    </section>
  </main>
</template>

<script setup>
import { computed, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ApiError } from '../api/client';
import { auth } from '../auth';
import AppIcon from '../components/AppIcon.vue';

const route = useRoute();
const router = useRouter();
const form = reactive({ username: '', password: '' });
const submitting = ref(false);
const error = ref('');
const expired = computed(() => route.query.reason === 'expired');

function safeRedirect() {
  const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/notes';
  return target.startsWith('/') && !target.startsWith('//') ? target : '/notes';
}

async function submit() {
  error.value = '';
  submitting.value = true;
  try {
    await auth.login(form.username, form.password);
    await router.replace(safeRedirect());
  } catch (requestError) {
    if (requestError instanceof ApiError && requestError.status === 401) {
      error.value = '用户名或密码不正确，请重新输入。';
    } else {
      error.value = '暂时无法连接服务器，请稍后重试。';
    }
    form.password = '';
  } finally {
    submitting.value = false;
  }
}
</script>
