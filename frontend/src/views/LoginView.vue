<template>
  <main class="login-page">
    <header class="login-header">
      <div class="login-brand" aria-label="个人工作台">
        <span class="brand-mark">知</span>
        <span>
          <strong>个人工作台</strong>
          <small>私人知识库</small>
        </span>
      </div>
      <span class="login-privacy"><span class="status-dot"></span>仅限所有者</span>
    </header>

    <div class="login-stage">
      <section class="login-intro" aria-labelledby="login-title">
        <span class="login-kicker">私人知识库</span>
        <h1 id="login-title">把想法放在<br><em>找得到的地方。</em></h1>
        <p>记录笔记，连接项目，用文件夹和标签把零散信息整理成可以继续使用的知识。</p>

        <div class="login-capabilities" aria-label="知识库功能">
          <span><AppIcon name="notes" />Markdown 笔记</span>
          <span><AppIcon name="projects" />项目关联</span>
          <span><AppIcon name="taxonomy" />文件夹与标签</span>
        </div>
      </section>

      <section class="login-sheet" aria-label="登录">
        <div class="sheet-binding" aria-hidden="true"><span></span><span></span><span></span></div>
        <span class="sheet-tab">OWNER ACCESS</span>
        <form class="login-form" @submit.prevent="submit">
          <div class="login-form-heading">
            <span class="section-kicker">欢迎回来</span>
            <h2>登录工作台</h2>
            <p>输入站点所有者账号，继续整理你的知识。</p>
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
              placeholder="例如 owner"
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
              placeholder="输入登录密码"
            >
          </label>

          <button class="login-submit" type="submit" :disabled="submitting">
            <span>{{ submitting ? '正在验证…' : '登录到工作台' }}</span>
            <AppIcon v-if="!submitting" name="arrow" />
          </button>

          <p class="login-footnote">登录状态由服务器安全保存，退出后立即失效。</p>
        </form>
      </section>
    </div>

    <footer class="login-footer">
      <span>WORKSTATION</span>
      <span>记录 · 连接 · 回看</span>
    </footer>
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
