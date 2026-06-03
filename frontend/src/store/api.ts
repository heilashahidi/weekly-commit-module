import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { getAccessToken } from '../auth/tokenProvider';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

interface HealthResponse {
  status: string;
}

export type RcdoNodeType =
  | 'RALLY_CRY'
  | 'DEFINING_OBJECTIVE'
  | 'OUTCOME'
  | 'SUPPORTING_OUTCOME';

/** Mirrors the backend RcdoNodeDto. `children` nests the subtree (full-tree
 * endpoint) or the node's direct children (single-node resolve). */
export interface RcdoNode {
  id: string;
  nodeType: RcdoNodeType;
  title: string;
  description: string | null;
  parentId: string | null;
  children: RcdoNode[];
}

/**
 * Project-wide RTK Query base slice. The only cross-cutting concern wired in the
 * base query is in-memory bearer-token injection. Feature endpoints declare their
 * own tag types for cache invalidation; RCDO is read-only today, so its tree
 * query only `providesTags` — the management workstream pairs `invalidatesTags`.
 */
export const api = createApi({
  reducerPath: 'api',
  tagTypes: ['RcdoNode'],
  baseQuery: fetchBaseQuery({
    baseUrl: API_BASE_URL,
    // Resolve fetch per call (not captured at module load) so the global is
    // always current — required for tests that stub global fetch.
    fetchFn: (input: RequestInfo | URL, init?: RequestInit) => globalThis.fetch(input, init),
    prepareHeaders: async (headers) => {
      const token = await getAccessToken();
      if (token) {
        headers.set('Authorization', `Bearer ${token}`);
      }
      return headers;
    },
  }),
  endpoints: (builder) => ({
    getHealth: builder.query<HealthResponse, void>({
      query: () => '/health',
    }),
    getRcdoTree: builder.query<RcdoNode[], void>({
      query: () => '/api/rcdo/tree',
      providesTags: ['RcdoNode'],
    }),
    getRcdoNode: builder.query<RcdoNode, string>({
      query: (id) => `/api/rcdo/nodes/${id}`,
      providesTags: (_result, _error, id) => [{ type: 'RcdoNode' as const, id }],
    }),
  }),
});

export const { useGetHealthQuery, useGetRcdoTreeQuery, useGetRcdoNodeQuery } = api;
