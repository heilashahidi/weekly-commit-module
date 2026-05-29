import { createApi, fetchBaseQuery } from '@reduxjs/toolkit/query/react';
import { getAccessToken } from '../auth/tokenProvider';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080';

interface HealthResponse {
  status: string;
}

/**
 * Project-wide RTK Query base slice. The only cross-cutting concern wired here is
 * in-memory bearer-token injection. Tag-based invalidation is the documented
 * convention for feature slices to adopt — no placeholder tags are scaffolded
 * here, since there are no consumers yet.
 */
export const api = createApi({
  reducerPath: 'api',
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
  }),
});

export const { useGetHealthQuery } = api;
