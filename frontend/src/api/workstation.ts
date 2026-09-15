import { createWorkstationApi } from '@workstation/api-client';
import { api, resetCsrfToken, submitWithCsrf } from './client';

export type {
  ApiErrorResponse,
  DashboardResponse,
  NoteDetailResponse,
  NoteFolderRequest,
  NoteFolderResponse,
  NoteImportRequest,
  NoteImportResponse,
  NoteListParams,
  NotePageResponse,
  NoteRequest,
  NoteSummaryResponse,
  ProjectDetailResponse,
  ProjectListParams,
  ProjectPageResponse,
  ProjectRequest,
  ProjectResponse,
  ProjectStatus,
  TagRequest,
  TagResponse,
  TaskListParams,
  TaskPageResponse,
  TaskRequest,
  TaskResponse,
  TaskStatus
} from '@workstation/api-client';

interface CurrentUserResponse {
  username: string;
  owner: boolean;
}

// Gateway BFF 接口不属于业务 OpenAPI 契约，类型在浏览器适配层本地维护。
export const authApi = {
  currentUser: () => api.get<CurrentUserResponse>('/auth/me', undefined, {
    suppressUnauthorized: true
  }),
  login(): void {
    // 这里只发起 OIDC 登录；用户名和密码始终由 Keycloak 页面收集。
    window.location.assign('/oauth2/authorization/keycloak');
  },
  async logout(): Promise<void> {
    await submitWithCsrf('/auth/logout');
    resetCsrfToken();
  }
};

const workstationApi = createWorkstationApi(api);

export const {
  dashboardApi,
  projectApi,
  taskApi,
  noteApi,
  noteFolderApi,
  tagApi
} = workstationApi;
