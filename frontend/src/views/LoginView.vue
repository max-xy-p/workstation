<template>
  <main class="login-page">
    <header class="login-header">
      <div class="login-brand" aria-label="Workstation 工作台">
        <span class="brand-mark">知</span>
        <span>
          <strong>Workstation</strong>
          <small>知识与项目工作台</small>
        </span>
      </div>
      <span class="login-privacy"><span class="status-dot"></span>工作空间协作</span>
    </header>

    <div class="login-stage">
      <section class="login-intro" aria-labelledby="login-title">
        <span class="login-kicker">团队知识与工作</span>
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
        <span class="sheet-tab">工作台登录</span>
        <div class="login-form">
          <div class="login-form-heading">
            <span class="section-kicker">欢迎回来</span>
            <h2>登录工作台</h2>
            <p>通过统一身份中心验证身份，继续整理你的知识。</p>
          </div>

          <output v-if="expired" class="login-notice">登录状态已失效，请重新验证。</output>
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
  const target = route.query.redirect;
  return typeof target === 'string' && target.startsWith('/') && !target.startsWith('//')
    ? target
    : null;
}

function submit() {
  submitting.value = true;
  const redirect = safeRedirect();
  if (redirect) sessionStorage.setItem('workstation:login-redirect', redirect);
  else sessionStorage.removeItem('workstation:login-redirect');
  auth.login();
}
</script>
