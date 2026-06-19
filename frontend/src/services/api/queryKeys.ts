/** TanStack Query key 팩토리 (안정적 캐시 키). */
import type { ActivityQueryType, FollowQueryType } from './types';

export const queryKeys = {
  overview: ['overview'] as const,
  follows: (type: FollowQueryType) => ['follows', type] as const,
  messageStats: ['messageStats'] as const,
  activity: (type: ActivityQueryType) => ['activity', type] as const,
  heatmap: ['heatmap'] as const,
  searches: ['searches'] as const,
  logins: ['logins'] as const,
  logs: ['logs'] as const,
  explorerTree: ['explorer', 'tree'] as const,
  explorerFile: (path: string) => ['explorer', 'file', path] as const,
  candidates: ['import', 'candidates'] as const,
  importStatus: ['import', 'status'] as const,
  uploadStatus: ['import', 'upload', 'status'] as const,
};
