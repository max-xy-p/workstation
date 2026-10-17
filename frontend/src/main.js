import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import './assets/styles.css';
import './assets/workspace-ui.css';

const app = createApp(App);
app.use(router);
router.isReady().then(() => app.mount('#app')).catch((error) => {
  console.error('无法初始化工作台', error);
  document.querySelector('#app').textContent = '工作台加载失败，请刷新页面重试。';
});
