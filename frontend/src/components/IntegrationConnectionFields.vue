<template>
  <div class="form-grid">
    <label>连接名称<input v-model="name" required maxlength="120" placeholder="例如：团队 GitHub" /></label>
    <label>平台<select v-model="provider" :disabled="editing"><option value="GITHUB">GitHub</option><option value="GITLAB">GitLab</option></select></label>
    <label class="wide">实例地址<input v-model="baseUrl" type="url" :disabled="editing" required maxlength="500" /></label>
    <label class="wide">只读访问 Token<input v-model="token" type="password" autocomplete="new-password" :required="!editing" maxlength="4096" :placeholder="editing ? '留空保留现有凭证' : '输入平台访问凭证'" /></label>
    <p class="form-hint wide">凭证仅用于服务端访问平台，不会回显。连接验证通过后，各类开发数据仍受平台授权范围限制。</p>
    <fieldset class="form-section">
      <legend>仓库发现范围</legend>
      <label class="form-section">发现范围<select v-model="scope"><option value="OWNED">本人拥有的仓库</option><option value="ACCESSIBLE">本人及参与的组织 / 协作仓库</option></select></label>
      <label class="check-field"><input v-model="includeForks" type="checkbox" /><span>包含 Fork 仓库</span></label>
      <label class="check-field"><input v-model="includeArchived" type="checkbox" /><span>包含归档仓库</span></label>
    </fieldset>
    <fieldset class="form-section">
      <legend>同步策略</legend>
      <label class="form-section">自动同步间隔（分钟）<input v-model.number="scheduleMinutes" type="number" min="0" max="10080" required /><span class="form-hint">填写 0 关闭自动同步，仍可手动执行。</span></label>
      <label class="check-field"><input v-model="autoImport" type="checkbox" /><span>自动导入新仓库<small>发现或定时同步时，为所选范围内未关联的新仓库创建规划中的项目。</small></span></label>
    </fieldset>
    <fieldset v-if="shared" class="form-section">
      <legend>工作空间共享</legend>
      <label class="check-field"><input v-model="sharingConfirmed" type="checkbox" required /><span>我确认导入的数据按当前空间成员权限共享<small>包括私有仓库。空间成员无需拥有对应平台的仓库访问权限即可查看已共享数据。</small></span></label>
    </fieldset>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue';
import type { IntegrationConnectionRequest } from '../api/workstation';
const props = defineProps<{ form: IntegrationConnectionRequest; editing: boolean; shared: boolean }>();
const emit = defineEmits<{ 'update:form': [value: IntegrationConnectionRequest] }>();
function field<K extends keyof IntegrationConnectionRequest>(key: K) {
  return computed({
    get: () => props.form[key],
    set: (value: IntegrationConnectionRequest[K]) => emit('update:form', { ...props.form, [key]: value })
  });
}
const name = field('name'), provider = field('provider'), baseUrl = field('baseUrl'), token = field('token');
const scope = field('scope'), includeForks = field('includeForks'), includeArchived = field('includeArchived');
const scheduleMinutes = field('scheduleMinutes'), autoImport = field('autoImport'), sharingConfirmed = field('sharingConfirmed');
</script>
