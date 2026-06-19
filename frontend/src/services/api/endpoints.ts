/**
 * 엔드포인트별 호출 함수. 컴포넌트/훅은 이 모듈만 사용한다.
 * 조회 9종은 { data, code } 반환(getJson), 임포트/업로드는 DTO 직접.
 */
import { getJson, getRaw, postJson, delJson, uploadFile } from './client';
import type {
  ActivityQueryType,
  ActivityResponse,
  CandidatesResponse,
  ExplorerFileResponse,
  ExplorerTreeResponse,
  FollowQueryType,
  FollowResponse,
  HeatmapResponse,
  ImportStatusResponse,
  LoginResponse,
  MessageStatsResponse,
  MiscLogResponse,
  OverviewResponse,
  SearchResponse,
  UploadStatusResponse,
} from './types';

export const api = {
  // ── 조회 9종 (envelope) ──────────────────────────────────────
  overview: () => getJson<OverviewResponse>('/api/overview'),
  follows: (type: FollowQueryType) => getJson<FollowResponse>(`/api/follows?type=${type}`),
  messageStats: () => getJson<MessageStatsResponse>('/api/messages/stats'),
  activity: (type: ActivityQueryType) => getJson<ActivityResponse>(`/api/activity?type=${type}`),
  heatmap: () => getJson<HeatmapResponse>('/api/heatmap'),
  searches: () => getJson<SearchResponse>('/api/searches'),
  logins: () => getJson<LoginResponse>('/api/logins'),
  logs: () => getJson<MiscLogResponse>('/api/logs'),
  explorerTree: () => getJson<ExplorerTreeResponse>('/api/explorer/tree'),
  explorerFile: (path: string) =>
    getJson<ExplorerFileResponse>(`/api/explorer/file?path=${encodeURIComponent(path)}`),

  // ── 임포트 / 업로드 (직접 DTO) ────────────────────────────────
  candidates: () => getRaw<CandidatesResponse>('/api/import/candidates'),
  importStatus: () => getRaw<ImportStatusResponse>('/api/import/status'),
  startImport: (folderPath: string) =>
    postJson<ImportStatusResponse>('/api/import', { folderPath }),
  resetImport: () => delJson<ImportStatusResponse>('/api/import'),
  resolveOwner: (username: string) =>
    postJson<ImportStatusResponse>('/api/import/owner', { username }),
  uploadStatus: () => getRaw<UploadStatusResponse>('/api/import/upload/status'),
  uploadExport: (file: File, onProgress?: (pct: number) => void) =>
    uploadFile<UploadStatusResponse>('/api/import/upload', file, onProgress),
};
