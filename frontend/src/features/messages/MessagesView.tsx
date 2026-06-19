import { useState } from 'react';
import { MessageSquare, Loader2, UserCheck, Clock, Crown, Send, Inbox } from 'lucide-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import {
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
} from 'recharts';
import { useMessageStats } from '@/services/api/hooks';
import { api } from '@/services/api/endpoints';
import { queryKeys } from '@/services/api/queryKeys';
import { errorCode, errorMessage } from '@/services/api/errorMessages';
import type { PartnerStat } from '@/services/api/types';
import { formatHourLabel } from '@/lib/format';
import { ViewHeader, ChartTooltip } from '@/components/ui';
import { cn } from '@/lib/cn';

export default function MessagesView() {
  const stats = useMessageStats();
  const ownerNeeded = errorCode(stats.query.error) === 'DM_OWNER_NOT_RESOLVED';

  return (
    <div className="space-y-8 animate-fade-in">
      <ViewHeader
        icon={MessageSquare}
        iconClassName="text-indigo-500"
        title="다이렉트 메시지 (DM) 통계"
        subtitle="대화방 수·총 메시지·Top10 대화 상대·시간대 분포를 집계합니다. (원문은 저장하지 않습니다)"
      />

      {stats.query.isLoading ? (
        <Center icon={Loader2} spin text="DM 통계를 불러오는 중…" />
      ) : ownerNeeded ? (
        <OwnerGate />
      ) : stats.query.isError ? (
        <Center icon={MessageSquare} text={errorMessage(stats.query.error)} />
      ) : (
        <StatsBody stats={stats.value} />
      )}
    </div>
  );
}

// ── owner 미해결 게이트 ─────────────────────────────────────────
function OwnerGate() {
  const qc = useQueryClient();
  const [username, setUsername] = useState('');
  const mutation = useMutation({
    mutationFn: (u: string) => api.resolveOwner(u),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: queryKeys.messageStats });
      qc.invalidateQueries({ queryKey: queryKeys.overview });
    },
  });

  return (
    <div className="max-w-md mx-auto rounded-3xl border border-pink-500/30 bg-pink-500/5 p-8 flex flex-col items-center gap-4 text-center">
      <UserCheck className="w-12 h-12 text-pink-500" />
      <h3 className="text-base font-bold text-slate-800 dark:text-white">본인 계정을 확정해 주세요</h3>
      <p className="text-xs text-slate-500 max-w-sm">
        보낸/받은 메시지를 구분하려면 본인 Instagram username 이 필요합니다. 자동 식별에 실패한 경우에만
        나타납니다.
      </p>
      <div className="flex gap-2 w-full">
        <input
          value={username}
          onChange={(e) => setUsername(e.target.value)}
          placeholder="your_username"
          className="flex-1 bg-ig-field dark:bg-ig-field-dark border border-ig-border dark:border-ig-border-dark rounded-xl px-3 py-2 text-xs text-slate-700 dark:text-slate-200 outline-none focus:border-pink-500/50"
        />
        <button
          onClick={() => username.trim() && mutation.mutate(username.trim())}
          disabled={!username.trim() || mutation.isPending}
          className="px-4 py-2 bg-pink-500 text-white font-semibold rounded-xl text-xs disabled:opacity-40 hover:bg-pink-600 transition-all"
        >
          {mutation.isPending ? '확정 중…' : '확정'}
        </button>
      </div>
      {mutation.isError && (
        <p className="text-[11px] text-rose-500">{errorMessage(mutation.error)}</p>
      )}
    </div>
  );
}

// ── 통계 본문 ───────────────────────────────────────────────────
function StatsBody({ stats }: { stats: ReturnType<typeof useMessageStats>['value'] }) {
  if (!stats) return <Center icon={MessageSquare} text="표시할 DM 통계가 없습니다." />;

  return (
    <div className="space-y-8">
      {/* 요약 카드 */}
      <div className="grid grid-cols-1 sm:grid-cols-2 gap-6">
        <SummaryCard label="대화방" value={stats.totalRooms.toLocaleString()} unit="개" icon={Inbox} />
        <SummaryCard
          label="총 메시지"
          value={stats.totalMessages.toLocaleString()}
          unit="개"
          icon={MessageSquare}
        />
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-5 gap-8">
        {/* 시간대 분포 */}
        <div className="lg:col-span-2 glass-panel p-6 rounded-2xl border border-ig-border dark:border-ig-border-dark shadow-sm relative overflow-hidden">
          <h3 className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-4 flex items-center gap-1.5">
            <Clock className="w-4 h-4 text-indigo-500" />
            시간대별 메시지 분포
          </h3>
          <HourlyBars hours={stats.hourlyDistribution} />
        </div>

        {/* Top10 대화상대 */}
        <div className="lg:col-span-3 glass-panel p-6 rounded-2xl border border-ig-border dark:border-ig-border-dark shadow-sm relative overflow-hidden">
          <h3 className="text-xs font-bold text-slate-400 uppercase tracking-widest mb-4 flex items-center gap-1.5">
            <Crown className="w-4 h-4 text-amber-500" />
            가장 많이 대화한 상대 Top {stats.topPartners.length}
          </h3>
          {stats.topPartners.length === 0 ? (
            <p className="text-xs text-slate-400 py-8 text-center">대화 상대 정보가 없습니다.</p>
          ) : (
            <div className="space-y-3 max-h-[460px] overflow-y-auto pr-2">
              {stats.topPartners.map((p, i) => (
                <PartnerRow key={p.partnerName + i} rank={i + 1} partner={p} />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

function SummaryCard({
  label,
  value,
  unit,
  icon: Icon,
}: {
  label: string;
  value: string;
  unit: string;
  icon: typeof Inbox;
}) {
  return (
    <div className="glass-panel p-6 rounded-2xl border border-ig-border dark:border-ig-border-dark shadow-sm relative overflow-hidden flex items-center justify-between">
      <div>
        <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">{label}</span>
        <p className="text-3xl font-extrabold text-slate-900 dark:text-white mt-1">
          {value}
          <span className="text-sm font-medium text-slate-400 ml-1">{unit}</span>
        </p>
      </div>
      <div className="p-3 rounded-2xl bg-indigo-500/10 text-indigo-500 border border-indigo-500/20">
        <Icon className="w-6 h-6" />
      </div>
    </div>
  );
}

function HourlyBars({ hours }: { hours: number[] }) {
  const data = hours.map((count, h) => ({ hour: h, count }));
  return (
    <div className="h-44">
      <ResponsiveContainer width="100%" height="100%">
        <BarChart data={data} margin={{ top: 8, right: 4, left: -20, bottom: 0 }}>
          <defs>
            <linearGradient id="hourlyBar" x1="0" y1="0" x2="0" y2="1">
              <stop offset="0%" stopColor="#6366f1" />
              <stop offset="100%" stopColor="#a855f7" />
            </linearGradient>
          </defs>
          <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="rgba(142,142,142,0.18)" />
          <XAxis
            dataKey="hour"
            tick={{ fontSize: 9, fill: '#8e8e8e' }}
            tickLine={false}
            axisLine={{ stroke: 'rgba(142,142,142,0.25)' }}
            ticks={[0, 6, 12, 18]}
          />
          <YAxis
            tick={{ fontSize: 9, fill: '#8e8e8e' }}
            tickLine={false}
            axisLine={false}
            width={32}
            allowDecimals={false}
          />
          <Tooltip
            cursor={{ fill: 'rgba(142,142,142,0.08)' }}
            content={
              <ChartTooltip
                unit="개"
                accentClassName="text-indigo-500"
                labelFormatter={(h) => formatHourLabel(Number(h))}
              />
            }
          />
          <Bar dataKey="count" fill="url(#hourlyBar)" radius={[4, 4, 0, 0]} maxBarSize={18} />
        </BarChart>
      </ResponsiveContainer>
    </div>
  );
}

function PartnerRow({ rank, partner: p }: { rank: number; partner: PartnerStat }) {
  const total = Math.max(1, p.sentCount + p.receivedCount);
  const sentPct = (p.sentCount / total) * 100;
  return (
    <div className="p-3 rounded-xl bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark">
      <div className="flex items-center justify-between gap-2 mb-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="text-[10px] text-slate-400 w-5 shrink-0">#{rank}</span>
          <span className="text-xs font-semibold text-slate-800 dark:text-white truncate">{p.partnerName}</span>
          {p.ownerInitiated && (
            <span className="text-[8px] px-1.5 py-0.5 rounded-full bg-pink-500/10 text-pink-500 border border-pink-500/20 shrink-0">
              내가 먼저
            </span>
          )}
        </div>
        <span className="text-[11px] font-bold text-indigo-500 shrink-0">
          {p.messageCount.toLocaleString()}
        </span>
      </div>
      <div className="h-1.5 rounded-full overflow-hidden flex bg-neutral-200 dark:bg-neutral-800">
        <div className="bg-gradient-to-r from-pink-500 to-purple-500 h-full" style={{ width: `${sentPct}%` }} />
        <div className="bg-slate-400 dark:bg-slate-600 h-full flex-1" />
      </div>
      <div className="flex justify-between text-[8px] text-slate-400 mt-1">
        <span className="flex items-center gap-0.5">
          <Send className="w-2.5 h-2.5" /> 보냄 {p.sentCount.toLocaleString()}
        </span>
        <span className="flex items-center gap-0.5">
          받음 {p.receivedCount.toLocaleString()} <Inbox className="w-2.5 h-2.5" />
        </span>
      </div>
    </div>
  );
}

function Center({ icon: Icon, text, spin }: { icon: typeof Loader2; text: string; spin?: boolean }) {
  return (
    <div className="flex flex-col items-center justify-center p-20 gap-3 text-slate-400">
      <Icon className={cn('w-10 h-10', spin && 'animate-spin')} />
      <p className="text-sm">{text}</p>
    </div>
  );
}
