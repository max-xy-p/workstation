async function listAllPages(transport, path, params) {
    const items = [];
    // 后一页是否存在由当前响应决定，保持逐页请求，避免并发越界读取。
    async function readPage(page) {
        const response = await transport.get(path, { ...params, page, size: 100 });
        items.push(...response.items);
        if (response.hasMore)
            return readPage(page + 1);
        return items;
    }
    return readPage(0);
}
export function createWorkstationApi(transport) {
    const workspacePath = (workspaceId) => `/workspaces/${workspaceId}`;
    return {
        webhookApi: {
            get: (w, id) => transport.get(`${workspacePath(w)}/integrations/${id}/webhook`),
            configure: (w, id, body) => transport.put(`${workspacePath(w)}/integrations/${id}/webhook`, body),
            deliveries: (w, id, page = 0, size = 20) => transport.get(`${workspacePath(w)}/integrations/${id}/webhook/deliveries`, { page, size }),
            retry: (w, id, delivery) => transport.post(`${workspacePath(w)}/integrations/${id}/webhook/deliveries/${delivery}/retry`),
        },
        integrationApi: {
            list: (w, page = 0, size = 20) => transport.get(`${workspacePath(w)}/integrations`, { page, size }),
            create: (w, body) => transport.post(`${workspacePath(w)}/integrations`, body),
            update: (w, id, body) => transport.patch(`${workspacePath(w)}/integrations/${id}`, body),
            validate: (w, id) => transport.post(`${workspacePath(w)}/integrations/${id}/validate`),
            enabled: (w, id, body) => transport.put(`${workspacePath(w)}/integrations/${id}/enabled`, body),
            delete: (w, id) => transport.delete(`${workspacePath(w)}/integrations/${id}`),
            discover: (w, id, requestKey) => transport.post(`${workspacePath(w)}/integrations/${id}/discoveries`, { requestKey }),
            sync: (w, id, requestKey) => transport.post(`${workspacePath(w)}/integrations/${id}/sync-runs`, { requestKey }),
            imports: (w, id, body) => transport.post(`${workspacePath(w)}/integrations/${id}/imports`, body),
            repositories: (w, id, page = 0, size = 20) => transport.get(`${workspacePath(w)}/integrations/${id}/repositories`, { page, size }),
            runs: (w, id, page = 0, size = 20) => transport.get(`${workspacePath(w)}/integrations/${id}/sync-runs`, { page, size }),
            run: (w, id, runId) => transport.get(`${workspacePath(w)}/integrations/${id}/sync-runs/${runId}`),
            items: (w, id, runId, page = 0, size = 20) => transport.get(`${workspacePath(w)}/integrations/${id}/sync-runs/${runId}/items`, { page, size }),
            projectRepositories: (w, project, page = 0, size = 20) => transport.get(`${workspacePath(w)}/projects/${project}/repositories`, { page, size }),
            bind: (w, project, body) => transport.post(`${workspacePath(w)}/projects/${project}/repositories`, body),
            unbind: (w, project, repository) => transport.delete(`${workspacePath(w)}/projects/${project}/repositories/${repository}`),
            development: (w, project) => transport.get(`${workspacePath(w)}/projects/${project}/development`),
            developmentItems: (w, project, kind, page = 0, size = 20) => transport.get(`${workspacePath(w)}/projects/${project}/development/items`, { kind, page, size }),
            workspaceDevelopment: (w, page = 0, size = 20) => transport.get(`${workspacePath(w)}/development/repositories`, { page, size }),
        },
        workspaceApi: {
            list: () => transport.get('/workspaces'),
            create: (payload) => transport.post('/workspaces', payload),
            get: (workspaceId) => transport.get(workspacePath(workspaceId)),
            update: (workspaceId, payload) => transport.put(workspacePath(workspaceId), payload),
            listMembers: (workspaceId) => transport.get(`${workspacePath(workspaceId)}/members`),
            findMemberCandidates: (workspaceId, username) => transport.get(`${workspacePath(workspaceId)}/member-candidates`, { username }),
            addMember: (workspaceId, payload) => transport.post(`${workspacePath(workspaceId)}/members`, payload),
            updateMember: (workspaceId, subjectId, role) => transport.put(`${workspacePath(workspaceId)}/members/${encodeURIComponent(subjectId)}`, { role }),
            removeMember: (workspaceId, subjectId) => transport.delete(`${workspacePath(workspaceId)}/members/${encodeURIComponent(subjectId)}`)
        },
        auditApi: {
            list: (workspaceId, params) => transport.get(`${workspacePath(workspaceId)}/audit-events`, params)
        },
        dashboardApi: {
            summary: (workspaceId) => transport.get(`${workspacePath(workspaceId)}/dashboard`)
        },
        projectApi: {
            list: (workspaceId, params) => transport.get(`${workspacePath(workspaceId)}/projects`, params),
            listAll: (workspaceId, params) => listAllPages(transport, `${workspacePath(workspaceId)}/projects`, params),
            detail: (workspaceId, id) => transport.get(`${workspacePath(workspaceId)}/projects/${id}`),
            create: (workspaceId, payload) => transport.post(`${workspacePath(workspaceId)}/projects`, payload),
            update: (workspaceId, id, payload) => transport.put(`${workspacePath(workspaceId)}/projects/${id}`, payload),
            updateStatus: (workspaceId, id, status) => transport.patch(`${workspacePath(workspaceId)}/projects/${id}/status`, { status }),
            delete: (workspaceId, id) => transport.delete(`${workspacePath(workspaceId)}/projects/${id}`)
        },
        issueApi: {
            confirm: (w, task, body) => transport.post(`${workspacePath(w)}/tasks/${task}/issue-source/confirm`, body),
            list: (w, projectId, page = 0, size = 20) => transport.get(`${workspacePath(w)}/issue-sources`, { projectId, page, size }),
            listAll: (w, projectId) => listAllPages(transport, `${workspacePath(w)}/issue-sources`, { projectId }),
            create: (w, project, body) => transport.post(`${workspacePath(w)}/projects/${project}/issue-tasks`, body),
            link: (w, project, body) => transport.post(`${workspacePath(w)}/projects/${project}/issue-links`, body),
            unlink: (w, task) => transport.delete(`${workspacePath(w)}/tasks/${task}/issue-source`),
        },
        taskApi: {
            list: (workspaceId, params) => transport.get(`${workspacePath(workspaceId)}/tasks`, params),
            listAll: (workspaceId, params) => listAllPages(transport, `${workspacePath(workspaceId)}/tasks`, params),
            get: (workspaceId, id) => transport.get(`${workspacePath(workspaceId)}/tasks/${id}`),
            create: (workspaceId, payload) => transport.post(`${workspacePath(workspaceId)}/tasks`, payload),
            update: (workspaceId, id, payload) => transport.put(`${workspacePath(workspaceId)}/tasks/${id}`, payload),
            updateStatus: (workspaceId, id, status) => transport.patch(`${workspacePath(workspaceId)}/tasks/${id}/status`, {
                status
            }),
            delete: (workspaceId, id) => transport.delete(`${workspacePath(workspaceId)}/tasks/${id}`)
        },
        noteApi: {
            revisions: (workspaceId, id, params) => transport.get(`${workspacePath(workspaceId)}/notes/${id}/revisions`, params),
            revision: (workspaceId, id, revisionId) => transport.get(`${workspacePath(workspaceId)}/notes/${id}/revisions/${revisionId}`),
            restoreRevision: (workspaceId, id, revisionId, expectedVersion) => transport.post(`${workspacePath(workspaceId)}/notes/${id}/revisions/${revisionId}/restore`, { expectedVersion }),
            list: (workspaceId, params) => transport.get(`${workspacePath(workspaceId)}/notes`, params),
            listAll: (workspaceId, params) => listAllPages(transport, `${workspacePath(workspaceId)}/notes`, params),
            listDeleted: (workspaceId, params) => transport.get(`${workspacePath(workspaceId)}/notes/trash`, params),
            get: (workspaceId, id) => transport.get(`${workspacePath(workspaceId)}/notes/${id}`),
            create: (workspaceId, payload) => transport.post(`${workspacePath(workspaceId)}/notes`, payload),
            update: (workspaceId, id, payload) => transport.put(`${workspacePath(workspaceId)}/notes/${id}`, payload),
            importMarkdown: (workspaceId, payload) => transport.post(`${workspacePath(workspaceId)}/notes/import`, payload),
            delete: (workspaceId, id) => transport.delete(`${workspacePath(workspaceId)}/notes/${id}`),
            restore: (workspaceId, id) => transport.post(`${workspacePath(workspaceId)}/notes/${id}/restore`),
            permanentlyDelete: (workspaceId, id) => transport.delete(`${workspacePath(workspaceId)}/notes/${id}/permanent`)
        },
        noteFolderApi: {
            list: (workspaceId) => transport.get(`${workspacePath(workspaceId)}/note-folders`),
            get: (workspaceId, id) => transport.get(`${workspacePath(workspaceId)}/note-folders/${id}`),
            create: (workspaceId, payload) => transport.post(`${workspacePath(workspaceId)}/note-folders`, payload),
            update: (workspaceId, id, payload) => transport.put(`${workspacePath(workspaceId)}/note-folders/${id}`, payload),
            delete: (workspaceId, id) => transport.delete(`${workspacePath(workspaceId)}/note-folders/${id}`)
        },
        tagApi: {
            list: (workspaceId) => transport.get(`${workspacePath(workspaceId)}/tags`),
            get: (workspaceId, id) => transport.get(`${workspacePath(workspaceId)}/tags/${id}`),
            create: (workspaceId, payload) => transport.post(`${workspacePath(workspaceId)}/tags`, payload),
            update: (workspaceId, id, payload) => transport.put(`${workspacePath(workspaceId)}/tags/${id}`, payload),
            delete: (workspaceId, id) => transport.delete(`${workspacePath(workspaceId)}/tags/${id}`)
        }
    };
}
export { createFetchTransport, WorkstationHttpError } from './fetch-transport.js';
