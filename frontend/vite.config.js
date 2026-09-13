import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';

export default defineConfig({
  plugins: [vue()],
  server: {
    proxy: {
      '/api': {
        target: process.env.VITE_DEV_GATEWAY_TARGET || 'http://localhost:8081',
        changeOrigin: false
      },
      '/oauth2': {
        target: process.env.VITE_DEV_GATEWAY_TARGET || 'http://localhost:8081',
        changeOrigin: false
      },
      '/login/oauth2': {
        target: process.env.VITE_DEV_GATEWAY_TARGET || 'http://localhost:8081',
        changeOrigin: false
      },
      '/auth': {
        target: process.env.VITE_DEV_KEYCLOAK_TARGET || 'http://localhost:8090',
        changeOrigin: true
      }
    }
  }
});
