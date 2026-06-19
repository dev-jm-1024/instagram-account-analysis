/**
 * 백엔드 REST DTO 에 1:1 대응하는 TS 타입.
 * 진실 소스: src/main/java/com/instagram/analyze/**\/dto/*.java (실측 교차검증).
 * Jackson non_null: null 필드 키는 응답에서 빠짐 → nullable 은 `?: T | null`.
 */

// ── 공통 envelope ────────────────────────────────────────────────
/** 조회 9종 공통 응답. 정상은 code=null. G4 는 200 + code(데이터 없음). */
export interface ApiResponse<T> {
  code: string | null;
  message: string | null;
  data: T | null;
}

/** 4xx/5xx 에러 본문. 분기는 httpStatus 가 아니라 code 로. */
export interface ErrorBody {
  code: string;
  message: string;
  detail?: string | null;
  httpStatus: number;
}

// ── 임포트 / 업로드 (envelope 없이 직접 반환) ───────────────────────
export type ImportStatus = 'IDLE' | 'IN_PROGRESS' | 'COMPLETED' | 'FAILED';
export type UploadStatus = 'IDLE' | 'SAVING' | 'EXTRACTING' | 'COMPLETED' | 'FAILED';

export interface ParseWarning {
  code: string;
  source: string;
  detail: string;
}

export interface ImportStatusResponse {
  status: ImportStatus;
  ownerResolved: boolean;
  durationMillis: number;
  parsedItemCount: number;
  warnings: ParseWarning[];
}

export interface UploadStatusResponse {
  status: UploadStatus;
  fileName?: string | null;
  extractedEntries: number;
  targetPath?: string | null;
  message?: string | null;
}

export interface ExportCandidate {
  path: string;
  name: string;
  account?: string | null;
  exportedAt?: string | null; // yyyy-MM-dd
}

export interface CandidatesResponse {
  dataRoot: string;
  autoImportSingle: boolean;
  candidates: ExportCandidate[];
}

// ── 조회 9종 (ApiResponse<T> 의 data) ──────────────────────────────
export interface OverviewData {
  followerCount: number;
  followingCount: number;
  mutualCount: number;
  iFollowOnlyCount: number;
  totalPostCount: number;
  conversationCount: number;
  messageCount: number;
  likeCount: number;
  commentCount: number;
  activityFrom?: number | null; // epoch ms
  activityTo?: number | null;
  mostActiveMonth?: string | null;
}

export interface OverviewResponse {
  importRequired: boolean;
  data?: OverviewData | null;
}

export interface FollowItem {
  username: string;
  followedAt?: number | null; // epoch ms; 불량 ts 면 키 부재
}

export interface FollowResponse {
  followerCount: number;
  followingCount: number;
  mutualCount: number;
  iFollowOnlyCount: number;
  followsMeOnlyCount: number;
  items: FollowItem[];
}

export interface PartnerStat {
  partnerName: string;
  messageCount: number;
  sentCount: number;
  receivedCount: number;
  ownerInitiated: boolean;
}

export interface MessageStatsResponse {
  totalRooms: number;
  totalMessages: number;
  topPartners: PartnerStat[];
  hourlyDistribution: number[]; // [24] — 글로벌
}

export interface MonthlyCount {
  month: string; // yyyy-MM
  count: number;
}

export interface ActivityResponse {
  type: string;
  total: number;
  monthlyCounts: MonthlyCount[];
}

export interface HeatmapPeak {
  dayOfWeek: number; // 0=월 … 6=일 (백엔드 기준)
  hour: number; // 0..23
  count: number;
}

export interface HeatmapResponse {
  grid: number[][]; // [7][24], grid[0]=월요일
  peak: HeatmapPeak;
}

export interface KeywordCount {
  keyword: string;
  count: number;
}

export interface SearchResponse {
  frequencies: KeywordCount[];
}

export interface LoginItem {
  timestamp: number; // epoch ms
  type: string; // LOGIN | LOGOUT
  ipAddress?: string | null;
  userAgent?: string | null;
}

export interface LoginResponse {
  timeline: LoginItem[];
}

export interface LogFileView {
  fileName: string;
  rows: Record<string, string>[];
  /** rows 와 같은 순서·길이로 정렬된 파싱·정규화된 epoch ms. 시각 미인식 행은 null. */
  timestamps?: (number | null)[];
}

export interface MiscLogResponse {
  files: LogFileView[];
}

export interface ExplorerNode {
  name: string;
  relativePath: string;
  directory: boolean;
  children: ExplorerNode[];
}

export interface ExplorerTreeResponse {
  root: ExplorerNode;
}

export interface ExplorerFileResponse {
  path: string;
  content: string;
  truncated: boolean;
}

// ── 팔로우 쿼리 타입 ───────────────────────────────────────────────
export type FollowQueryType =
  | 'ALL'
  | 'FOLLOWERS'
  | 'FOLLOWING'
  | 'MUTUAL'
  | 'I_FOLLOW_ONLY'
  | 'FOLLOWS_ME_ONLY'
  | 'UNFOLLOWED'
  | 'PENDING'
  | 'CLOSE_FRIENDS'
  | 'RESTRICTED';

export type ActivityQueryType = 'post' | 'like' | 'comment' | 'saved';

// ── G4 "데이터 없음" code ──────────────────────────────────────────
export const NOT_FOUND_CODES = [
  'SEARCH_HISTORY_NOT_FOUND',
  'LOGIN_HISTORY_NOT_FOUND',
  'MISC_LOG_DIR_NOT_FOUND',
] as const;
