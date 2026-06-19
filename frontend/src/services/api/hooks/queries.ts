/**
 * 조회 9종 쿼리 훅. 각 훅은 표준 useQuery 결과에
 *  - value: 언랩된 DTO (null 가능)
 *  - code:  envelope code (정상 null, G4 시 *_NOT_FOUND)
 *  - notFound: G4 빈-소스 여부
 * 를 덧붙여 반환한다. 에러(ApiError)는 query.error 로 노출.
 */
import { useQuery, type UseQueryResult } from '@tanstack/react-query';
import { api } from '../endpoints';
import { queryKeys } from '../queryKeys';
import {
  NOT_FOUND_CODES,
  type ActivityQueryType,
  type ActivityResponse,
  type ExplorerFileResponse,
  type ExplorerTreeResponse,
  type FollowQueryType,
  type FollowResponse,
  type HeatmapResponse,
  type LoginResponse,
  type MessageStatsResponse,
  type MiscLogResponse,
  type OverviewResponse,
  type SearchResponse,
} from '../types';

type Envelope<T> = { data: T | null; code: string | null };

interface EnvelopeQuery<T> {
  query: UseQueryResult<Envelope<T>, Error>;
  value: T | null;
  code: string | null;
  notFound: boolean;
}

function decorate<T>(query: UseQueryResult<Envelope<T>, Error>): EnvelopeQuery<T> {
  const code = query.data?.code ?? null;
  return {
    query,
    value: query.data?.data ?? null,
    code,
    notFound: code != null && (NOT_FOUND_CODES as readonly string[]).includes(code),
  };
}

export function useOverview() {
  return decorate(
    useQuery({ queryKey: queryKeys.overview, queryFn: () => api.overview() }),
  ) as EnvelopeQuery<OverviewResponse>;
}

export function useFollows(type: FollowQueryType) {
  return decorate(
    useQuery({ queryKey: queryKeys.follows(type), queryFn: () => api.follows(type) }),
  ) as EnvelopeQuery<FollowResponse>;
}

export function useMessageStats() {
  return decorate(
    useQuery({ queryKey: queryKeys.messageStats, queryFn: () => api.messageStats() }),
  ) as EnvelopeQuery<MessageStatsResponse>;
}

export function useActivity(type: ActivityQueryType) {
  return decorate(
    useQuery({ queryKey: queryKeys.activity(type), queryFn: () => api.activity(type) }),
  ) as EnvelopeQuery<ActivityResponse>;
}

export function useHeatmap() {
  return decorate(
    useQuery({ queryKey: queryKeys.heatmap, queryFn: () => api.heatmap() }),
  ) as EnvelopeQuery<HeatmapResponse>;
}

export function useSearches() {
  return decorate(
    useQuery({ queryKey: queryKeys.searches, queryFn: () => api.searches() }),
  ) as EnvelopeQuery<SearchResponse>;
}

export function useLogins() {
  return decorate(
    useQuery({ queryKey: queryKeys.logins, queryFn: () => api.logins() }),
  ) as EnvelopeQuery<LoginResponse>;
}

export function useMiscLogs() {
  return decorate(
    useQuery({ queryKey: queryKeys.logs, queryFn: () => api.logs() }),
  ) as EnvelopeQuery<MiscLogResponse>;
}

export function useExplorerTree() {
  return decorate(
    useQuery({ queryKey: queryKeys.explorerTree, queryFn: () => api.explorerTree() }),
  ) as EnvelopeQuery<ExplorerTreeResponse>;
}

export function useExplorerFile(path: string | null) {
  return decorate(
    useQuery({
      queryKey: queryKeys.explorerFile(path ?? ''),
      queryFn: () => api.explorerFile(path as string),
      enabled: path != null && path.length > 0,
    }),
  ) as EnvelopeQuery<ExplorerFileResponse>;
}

export type { EnvelopeQuery };
