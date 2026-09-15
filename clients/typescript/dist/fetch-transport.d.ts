import type { ApiErrorResponse, WorkstationTransport } from './index.js';
export type TokenProvider = () => string | undefined | Promise<string | undefined>;
export interface FetchTransportOptions {
    baseUrl: string;
    fetch?: typeof globalThis.fetch;
    headers?: HeadersInit;
    token?: string | TokenProvider;
}
export declare class WorkstationHttpError extends Error {
    readonly status: number;
    readonly response?: Partial<ApiErrorResponse>;
    constructor(message: string, status: number, response?: Partial<ApiErrorResponse>);
}
export declare function createFetchTransport(options: FetchTransportOptions): WorkstationTransport;
