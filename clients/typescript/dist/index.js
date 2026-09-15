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
    return {
        dashboardApi: {
            summary: () => transport.get('/dashboard')
        },
        projectApi: {
            list: (params) => transport.get('/projects', params),
            listAll: (params) => listAllPages(transport, '/projects', params),
            detail: (id) => transport.get(`/projects/${id}`),
            create: (payload) => transport.post('/projects', payload),
            update: (id, payload) => transport.put(`/projects/${id}`, payload),
            updateStatus: (id, status) => transport.patch(`/projects/${id}/status`, { status }),
            delete: (id) => transport.delete(`/projects/${id}`)
        },
        taskApi: {
            list: (params) => transport.get('/tasks', params),
            listAll: (params) => listAllPages(transport, '/tasks', params),
            get: (id) => transport.get(`/tasks/${id}`),
            create: (payload) => transport.post('/tasks', payload),
            update: (id, payload) => transport.put(`/tasks/${id}`, payload),
            updateStatus: (id, status) => transport.patch(`/tasks/${id}/status`, {
                status
            }),
            delete: (id) => transport.delete(`/tasks/${id}`)
        },
        noteApi: {
            list: (params) => transport.get('/notes', params),
            listAll: (params) => listAllPages(transport, '/notes', params),
            get: (id) => transport.get(`/notes/${id}`),
            create: (payload) => transport.post('/notes', payload),
            update: (id, payload) => transport.put(`/notes/${id}`, payload),
            importMarkdown: (payload) => transport.post('/notes/import', payload),
            delete: (id) => transport.delete(`/notes/${id}`)
        },
        noteFolderApi: {
            list: () => transport.get('/note-folders'),
            get: (id) => transport.get(`/note-folders/${id}`),
            create: (payload) => transport.post('/note-folders', payload),
            update: (id, payload) => transport.put(`/note-folders/${id}`, payload),
            delete: (id) => transport.delete(`/note-folders/${id}`)
        },
        tagApi: {
            list: () => transport.get('/tags'),
            get: (id) => transport.get(`/tags/${id}`),
            create: (payload) => transport.post('/tags', payload),
            update: (id, payload) => transport.put(`/tags/${id}`, payload),
            delete: (id) => transport.delete(`/tags/${id}`)
        }
    };
}
export { createFetchTransport, WorkstationHttpError } from './fetch-transport.js';
