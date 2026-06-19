import { useState } from 'react';
import {
  FileText,
  Compass,
  Loader2,
  BarChart3,
  Camera,
  Heart,
  MessageCircle,
  Bookmark,
  type LucideIcon,
} from 'lucide-react';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { useActivity } from '@/services/api/hooks';
import type { ActivityQueryType, MonthlyCount } from '@/services/api/types';
import { ViewHeader, PillTabs, EmptyState, ChartTooltip } from '@/components/ui';
import { cn } from '@/lib/cn';

const TABS: { id: ActivityQueryType; label: string; icon: LucideIcon; accent: string }[] = [
  { id: 'post', label: '게시물', icon: Camera, accent: 'text-pink-500' },
  { id: 'like', label: '좋아요', icon: Heart, accent: 'text-rose-500' },
  { id: 'comment', label: '댓글', icon: MessageCircle, accent: 'text-purple-500' },
  { id: 'saved', label: '저장', icon: Bookmark, accent: 'text-amber-500' },
];

export default function ActivityView() {
  const [activeTab, setActiveTab] = useState<ActivityQueryType>('post');

  // 4 타입 모두 조회(정적·캐시) → 탭 배지 총계 + 활성 월별 추이.
  const q = {
    post: useActivity('post'),
    like: useActivity('like'),
    comment: useActivity('comment'),
    saved: useActivity('saved'),
  } satisfies Record<ActivityQueryType, ReturnType<typeof useActivity>>;

  const active = q[activeTab];
  const monthly = active.value?.monthlyCounts ?? [];

  return (
    <div className="space-y-8 animate-fade-in relative z-10">
      <ViewHeader
        icon={FileText}
        iconClassName="text-indigo-500"
        title="활동 추이 분석"
        subtitle="게시물·좋아요·댓글·저장의 월별 추이와 총계를 집계합니다. (개별 항목은 탐색기에서 raw 로 확인)"
        action={
          <PillTabs<ActivityQueryType>
            active={activeTab}
            onChange={setActiveTab}
            tabs={TABS.map((t) => {
              const Icon = t.icon;
              return {
                id: t.id,
                label: (
                  <span className="flex items-center gap-1.5">
                    <Icon className="w-3.5 h-3.5" />
                    {t.label} ({q[t.id].value?.total ?? 0})
                  </span>
                ),
              };
            })}
          />
        }
      />

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
        {/* Insights */}
        <div className="lg:col-span-1 space-y-6">
          <div className="glass-panel p-5 rounded-2xl border border-ig-border dark:border-ig-border-dark shadow-sm relative overflow-hidden">
            <h3 className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-4">활동 총계</h3>
            <div className="space-y-3 text-xs">
              {TABS.map((t) => {
                const Icon = t.icon;
                return (
                  <div key={t.id} className="flex justify-between items-baseline">
                    <span className="flex items-center gap-1.5 text-slate-500 dark:text-slate-400">
                      <Icon className={cn('w-3.5 h-3.5', t.accent)} />
                      {t.label}
                    </span>
                    <span className={cn('font-bold', t.accent)}>
                      {(q[t.id].value?.total ?? 0).toLocaleString()}건
                    </span>
                  </div>
                );
              })}
            </div>
          </div>
        </div>

        {/* Monthly trend */}
        <div className="lg:col-span-3 glass-panel p-6 rounded-3xl border border-ig-border dark:border-ig-border-dark shadow-sm min-h-[500px] flex flex-col relative overflow-hidden">
          {active.query.isLoading ? (
            <EmptyState icon={Loader2} spin title="불러오는 중…" />
          ) : monthly.length === 0 ? (
            <EmptyState
              icon={Compass}
              spin
              title="표시할 활동 추이가 없습니다."
              description="해당 카테고리에 집계된 활동이 없습니다."
            />
          ) : (
            <>
              <div className="flex items-baseline justify-between mb-6">
                <div>
                  <span className="text-[10px] uppercase tracking-widest text-slate-400 flex items-center gap-1.5">
                    <BarChart3 className="w-3.5 h-3.5" />
                    월별 추이
                  </span>
                  <h3 className="text-2xl font-extrabold text-slate-800 dark:text-white mt-1">
                    총 {(active.value?.total ?? 0).toLocaleString()}건
                  </h3>
                </div>
                <span className="text-xs text-slate-400">{monthly.length}개월 기록</span>
              </div>
              <MonthlyBars data={monthly} />
            </>
          )}
        </div>
      </div>
    </div>
  );
}

function MonthlyBars({ data }: { data: MonthlyCount[] }) {
  const sorted = [...data].sort((a, b) => a.month.localeCompare(b.month));
  return (
    <div className="flex-1 min-h-[420px]">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={sorted} margin={{ top: 8, right: 8, left: -12, bottom: 8 }}>
          <defs>
            <linearGradient id="activityBar" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#a855f7" />
              <stop offset="100%" stopColor="#ec4899" />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(142,142,142,0.18)" />
          <XAxis
            dataKey="month"
            tick={{ fontSize: 10, fill: '#8e8e8e' }}
            tickLine={false}
            axisLine={{ stroke: 'rgba(142,142,142,0.25)' }}
            interval="preserveStartEnd"
            minTickGap={12}
          />
          <YAxis
            tick={{ fontSize: 10, fill: '#8e8e8e' }}
            tickLine={false}
            axisLine={false}
            width={40}
            allowDecimals={false}
          />
          <Tooltip cursor={{ fill: 'rgba(142,142,142,0.08)' }} content={<ChartTooltip unit="건" />} />
          <Bar dataKey="count" fill="url(#activityBar)" radius={[6, 6, 0, 0]} maxBarSize={44} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}
