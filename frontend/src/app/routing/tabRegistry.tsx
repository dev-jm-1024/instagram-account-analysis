/* eslint-disable react-refresh/only-export-components -- this is a tab registry, not a component module */
import { lazy, type ComponentType } from 'react';
import {
  BarChart3,
  Users,
  MessageCircle,
  FileText,
  Calendar,
  Search,
  ShieldCheck,
  Layers,
  Code,
  UploadCloud,
  type LucideIcon,
} from 'lucide-react';
import type { ActiveTab, NavGroup } from '@/domain/types';

// Each view is code-split into its own chunk (React.lazy + dynamic import) so the
// initial bundle stays small — heavy deps like Recharts only load with their tab.
const Overview = lazy(() => import('@/features/overview/Overview'));
const Relations = lazy(() => import('@/features/relations/Relations'));
const MessagesView = lazy(() => import('@/features/messages/MessagesView'));
const ActivityView = lazy(() => import('@/features/activity/ActivityView'));
const ActivityHeatmap = lazy(() => import('@/features/activity/ActivityHeatmap'));
const RawExplorer = lazy(() => import('@/features/explorer/RawExplorer'));
const Uploader = lazy(() => import('@/features/uploader/Uploader'));

// LogsView is one component reused for three tabs via its `subMode` prop.
const LogsView = lazy(() => import('@/features/logs/LogsView'));
const SearchLogs = () => <LogsView subMode="searches" />;
const SecurityLogs = () => <LogsView subMode="logins" />;
const MiscLogs = () => <LogsView subMode="misc_logs" />;

export interface TabDefinition {
  id: ActiveTab;
  label: string;
  icon: LucideIcon;
  group: NavGroup;
  Component: ComponentType;
}

/**
 * Single source of truth for tabs: navigation labels/icons AND the view that
 * renders for each tab. Adding a tab = adding one entry here (OCP) — the sidebar
 * menu and the content router both derive from this list, so they never drift.
 */
export const TAB_REGISTRY: TabDefinition[] = [
  { id: 'overview', label: '요약 대시보드', icon: BarChart3, group: '핵심', Component: Overview },
  { id: 'followers', label: '팔로우 보기', icon: Users, group: '관계', Component: Relations },
  { id: 'dm', label: 'DM 통계', icon: MessageCircle, group: '관계', Component: MessagesView },
  { id: 'activity', label: '활동 기록', icon: FileText, group: '활동', Component: ActivityView },
  { id: 'heatmap', label: '활동 히트맵', icon: Calendar, group: '활동', Component: ActivityHeatmap },
  { id: 'search', label: '검색 기록', icon: Search, group: '활동', Component: SearchLogs },
  { id: 'security', label: '로그인 기록', icon: ShieldCheck, group: '계정', Component: SecurityLogs },
  { id: 'misc_logs', label: '활동·추적 기록', icon: Layers, group: '계정', Component: MiscLogs },
  { id: 'explorer', label: '데이터 탐색기', icon: Code, group: '도구', Component: RawExplorer },
  { id: 'uploader', label: '데이터 업로드', icon: UploadCloud, group: '도구', Component: Uploader },
];

export function getTabDefinition(tab: ActiveTab): TabDefinition {
  return TAB_REGISTRY.find((t) => t.id === tab) ?? TAB_REGISTRY[0];
}
