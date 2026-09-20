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
        <span class="sheet-tab">WORKSTATION ACCESS</span>
        <div class="login-form">
          <div class="login-form-heading">
            <span class="section-kicker">欢迎回来</span>
            <h2>登录工作台</h2>
            <p>通过统一身份中心验证身份，继续整理你的知识。</p>
          </div>

          <p v-if="expired" class="login-notice" role="status">登录状态已失效，请重新验证。</p>
          <button class="login-submit" type="button" :disabled="submitting" @click="submit">
            <span>{{ submitting ? '正在前往身份中心…' : '使用统一身份登录' }}</span>
            <AppIcon v-if="!submitting" name="arrow" />
          </button>

          <p class="login-footnote">账号凭据只提交给 Keycloak，工作台不会在浏览器中保存访问令牌。</p>
        </div>
      </section>
    </div>

    <footer class="login-footer">
      <span>WORKSTATION</span>
      <span>记录 · 连接 · 回看</span>
    </footer>
  </main>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRoute } from 'vue-router';
import { auth } from '../auth';
import AppIcon from '../components/AppIcon.vue';

const route = useRoute();
const submitting = ref(false);
const expired = computed(() => route.query.reason === 'expired');

function safeRedirect() {
  const target = typeof route.query.redirect === 'string' ? route.query.redirect : '/notes';
  return target.startsWith('/') && !target.startsWith('//') ? target : '/notes';
}

function submit() {
  submitting.value = true;
  sessionStorage.setItem('workstation:login-redirect', safeRedirect());
  auth.login();
}
</script>
