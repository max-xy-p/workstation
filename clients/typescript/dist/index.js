async function listAllPages(transport, path, params) {
    const items = [];
    let page = 0;
    let response;
    do {
        response = await transport.get(path, { ...params, page, size: 100 });
        items.push(...response.items);
        page += 1;
    } while (response.hasMore);
    return items;
}
export function createWorkstationApi(transport) {
    const workspacePath = (workspaceId) => `/workspaces/${workspaceId}`;
    return {
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
