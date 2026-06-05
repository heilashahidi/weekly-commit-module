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

// ---------------------------------------------------------------------------
// Lifecycle types (mirror workstream C's DTOs / enums — exact backend shapes).
// Co-located here like RcdoNode; consumed by the adaptive "My Week" screens.
// ---------------------------------------------------------------------------

/** Plan lifecycle state (drives the adaptive view). */
export type PlanStatus = 'DRAFT' | 'LOCKED' | 'RECONCILING' | 'RECONCILED';

/** Per-commitment reconciliation outcome. `UNRECONCILED` is system-set only. */
export type ReconciliationStatus =
  | 'DONE'
  | 'PARTIAL'
  | 'NOT_DONE'
  | 'DROPPED'
  | 'UNRECONCILED';

/** How a plan reached LOCKED: a deliberate user action or the auto-backstop. */
export type LockType = 'USER_LOCKED' | 'AUTO_LOCKED';

/** Mirrors backend WeeklyPlanDto. `statusDeadline` is an ISO-8601 Instant. */
export interface WeeklyPlanDto {
  id: string;
  owner: string;
  weekKey: string;
  status: PlanStatus;
  lockType: LockType | null;
  noPlan: boolean;
  statusDeadline: string | null;
  commitmentCount: number;
}

/** Mirrors backend CommitmentDto. The `rcdoNodeId` is the always-visible spine. */
export interface CommitmentDto {
  id: string;
  weeklyPlanId: string;
  rcdoNodeId: string;
  title: string;
  planned: boolean;
  reconciliationStatus: ReconciliationStatus | null;
  reconciliationNote: string | null;
  carriedFromId: string | null;
  carryWeekCount: number;
}

/**
 * Mirrors backend PlanMetricsDto. `reconciliationAccuracy` is nullable: `null`
 * means "not applicable" (zero planned), distinct from `0.0`.
 */
export interface PlanMetricsDto {
  planId: string;
  plannedCount: number;
  unplannedCount: number;
  doneCount: number;
  reconciliationAccuracy: number | null;
  plannedVsUnplannedRatio: number;
}

/** Mirrors backend CarryCandidateDto — a PARTIAL/NOT_DONE planned commitment. */
export interface CarryCandidateDto {
  id: string;
  title: string;
  rcdoNodeId: string;
  reconciliationStatus: ReconciliationStatus;
  carryWeekCount: number;
}

/** Mirrors backend ManagerReviewDto. `reviewedAt` is an ISO-8601 Instant. */
export interface ManagerReviewDto {
  id: string;
  weeklyPlanId: string;
  reviewer: string;
  comment: string;
  reviewedAt: string;
}

// ---------------------------------------------------------------------------
// Manager dashboard types (workstream F — mirror backend manager.* DTOs).
// ---------------------------------------------------------------------------

/** One outcome-spread chip: an RCDO Outcome title (or "Other") and its count. */
export interface OutcomeCountDto {
  outcome: string;
  count: number;
}

/**
 * One row of the manager's current-week team board. `status` is `null` when the
 * report has no current-week plan yet — a first-class "no plan" signal, NOT a
 * `PlanStatus` value (so it is never fed to the status-style map).
 */
export interface TeamRowDto {
  reportSub: string;
  displayName: string;
  /** The report's current-week plan id, or `null` when they have no plan yet. */
  planId: string | null;
  status: PlanStatus | null;
  outcomeSpread: OutcomeCountDto[];
  reviewExists: boolean;
}

/** Mirrors backend PageDto — content plus the metadata a client needs to page. */
export interface PageDto<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Project-wide RTK Query base slice. The only cross-cutting concern wired in the
 * base query is in-memory bearer-token injection. Feature endpoints declare their
 * own tag types for cache invalidation; RCDO is read-only today, so its tree
 * query only `providesTags` — the management workstream pairs `invalidatesTags`.
 */
export const api = createApi({
  reducerPath: 'api',
  tagTypes: [
    'RcdoNode',
    'WeeklyPlan',
    'Commitment',
    'CarryCandidate',
    'ManagerReview',
    'PlanMetrics',
    'TeamWeek',
  ],
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

    // -- Lifecycle queries (providesTags drive auto-refresh after mutations) --

    getCurrentPlan: builder.query<WeeklyPlanDto, void>({
      query: () => '/api/lifecycle/plans/current',
      providesTags: ['WeeklyPlan'],
    }),
    getPlan: builder.query<WeeklyPlanDto, string>({
      query: (id) => `/api/lifecycle/plans/${id}`,
      // Provide BOTH the id-scoped tag and the unscoped 'WeeklyPlan' tag: the
      // lifecycle mutations invalidate the unscoped tag (matching getCurrentPlan),
      // so without the unscoped entry a plan fetched by id would never refetch
      // after a lock/transition. The id-scoped tag is kept for future per-id use.
      providesTags: (_result, _error, id) => [{ type: 'WeeklyPlan' as const, id }, 'WeeklyPlan'],
    }),
    getPlanCommitments: builder.query<CommitmentDto[], string>({
      query: (planId) => `/api/lifecycle/plans/${planId}/commitments`,
      providesTags: ['Commitment'],
    }),
    getPlanMetrics: builder.query<PlanMetricsDto, string>({
      query: (planId) => `/api/lifecycle/plans/${planId}/metrics`,
      providesTags: ['PlanMetrics'],
    }),
    getCarryCandidates: builder.query<CarryCandidateDto[], string>({
      query: (planId) => `/api/lifecycle/plans/${planId}/carry-candidates`,
      providesTags: ['CarryCandidate'],
    }),
    /**
     * The review GET returns 204 No Content when no review exists. fetchBaseQuery
     * would otherwise try to JSON-parse the empty body and fail; a custom
     * responseHandler returns `null` for a 204 (and parses JSON otherwise) so the
     * hook resolves with `data: null` and no error — absence is a valid state.
     */
    getManagerReview: builder.query<ManagerReviewDto | null, string>({
      query: (planId) => ({
        url: `/api/lifecycle/plans/${planId}/review`,
        // Read text first, then parse: a 204 (no review) AND any empty-body error
        // response (e.g. a 403/500 with no ProblemDetail body) both yield null
        // instead of throwing a PARSING_ERROR that would mask the real status.
        responseHandler: async (response) => {
          const text = await response.text();
          return text ? JSON.parse(text) : null;
        },
      }),
      providesTags: ['ManagerReview'],
    }),

    // -- Manager dashboard (workstream F) --

    /**
     * The manager's current-week team board, paginated over their direct reports.
     * Defaults to the first page at a large size — v1 teams are small, so the board
     * renders a single page with no paging controls (a report beyond `size` would be
     * omitted; revisit with the deferred trends work).
     */
    getTeamWeek: builder.query<PageDto<TeamRowDto>, { page?: number; size?: number } | void>({
      query: (arg) => {
        const page = arg?.page ?? 0;
        const size = arg?.size ?? 50;
        return `/api/manager/team?page=${page}&size=${size}&sort=reportDisplayName,asc`;
      },
      providesTags: ['TeamWeek'],
    }),

    // -- Lifecycle mutations (invalidatesTags refetch the affected queries) --

    createCommitment: builder.mutation<
      CommitmentDto,
      { planId: string; body: { rcdoNodeId: string; title: string } }
    >({
      query: ({ planId, body }) => ({
        url: `/api/plans/${planId}/commitments`,
        method: 'POST',
        body,
      }),
      invalidatesTags: ['Commitment', 'WeeklyPlan'],
    }),
    updateCommitment: builder.mutation<
      CommitmentDto,
      { id: string; body: { rcdoNodeId: string; title: string } }
    >({
      query: ({ id, body }) => ({
        url: `/api/commitments/${id}`,
        method: 'PUT',
        body,
      }),
      invalidatesTags: ['Commitment'],
    }),
    deleteCommitment: builder.mutation<void, string>({
      query: (id) => ({
        url: `/api/commitments/${id}`,
        method: 'DELETE',
      }),
      invalidatesTags: ['Commitment', 'WeeklyPlan'],
    }),
    lock: builder.mutation<WeeklyPlanDto, string>({
      query: (planId) => ({
        url: `/api/lifecycle/plans/${planId}/transitions/lock`,
        method: 'POST',
      }),
      invalidatesTags: ['WeeklyPlan'],
    }),
    startReconciling: builder.mutation<WeeklyPlanDto, string>({
      query: (planId) => ({
        url: `/api/lifecycle/plans/${planId}/transitions/start-reconciling`,
        method: 'POST',
      }),
      invalidatesTags: ['WeeklyPlan'],
    }),
    setCommitmentStatus: builder.mutation<
      CommitmentDto,
      { id: string; body: { status: ReconciliationStatus; note: string | null } }
    >({
      query: ({ id, body }) => ({
        url: `/api/lifecycle/commitments/${id}/status`,
        method: 'PUT',
        body,
      }),
      invalidatesTags: ['Commitment', 'PlanMetrics'],
    }),
    submitReconciled: builder.mutation<WeeklyPlanDto, string>({
      query: (planId) => ({
        url: `/api/lifecycle/plans/${planId}/transitions/submit-reconciled`,
        method: 'POST',
      }),
      invalidatesTags: ['WeeklyPlan', 'Commitment', 'PlanMetrics'],
    }),
    carry: builder.mutation<
      // Backend CarryForwardController.carry returns the seeded next-week
      // commitments (List<CommitmentDto>), not a plan.
      CommitmentDto[],
      { planId: string; body: { commitmentIds: string[] } }
    >({
      query: ({ planId, body }) => ({
        url: `/api/lifecycle/plans/${planId}/carry`,
        method: 'POST',
        body,
      }),
      invalidatesTags: ['WeeklyPlan', 'Commitment', 'CarryCandidate'],
    }),

    /**
     * Manager writes (creates or updates) the single non-blocking review for a plan
     * (workstream F). Invalidates the review (so the detail view refreshes) and the
     * team board (so the review-done flag updates).
     */
    upsertManagerReview: builder.mutation<
      ManagerReviewDto,
      { planId: string; comment: string }
    >({
      query: ({ planId, comment }) => ({
        url: `/api/lifecycle/plans/${planId}/review`,
        method: 'PUT',
        body: { comment },
      }),
      invalidatesTags: ['ManagerReview', 'TeamWeek'],
    }),
  }),
});

export const {
  useGetHealthQuery,
  useGetRcdoTreeQuery,
  useGetRcdoNodeQuery,
  useGetCurrentPlanQuery,
  useGetPlanQuery,
  useGetPlanCommitmentsQuery,
  useGetPlanMetricsQuery,
  useGetCarryCandidatesQuery,
  useGetManagerReviewQuery,
  useGetTeamWeekQuery,
  useUpsertManagerReviewMutation,
  useCreateCommitmentMutation,
  useUpdateCommitmentMutation,
  useDeleteCommitmentMutation,
  useLockMutation,
  useStartReconcilingMutation,
  useSetCommitmentStatusMutation,
  useSubmitReconciledMutation,
  useCarryMutation,
} = api;
