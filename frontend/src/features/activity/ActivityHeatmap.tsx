import { useMemo, useState } from 'react';
import { Calendar, Clock, Smile, Loader2, UploadCloud } from 'lucide-react';
import { useNavigation } from '@/app/routing/useNavigation';
import { useHeatmap } from '@/services/api/hooks';
import { errorCode } from '@/services/api/errorMessages';
import { formatHourLabel } from '@/lib/format';
import { cn } from '@/lib/cn';
import { ViewHeader } from '@/components/ui';

// 프론트 요일 축: 0=일 … 6=토. 백엔드 grid/peak.dayOfWeek 는 0=월 … 6=일.
const DAYS = ['일요일', '월요일', '화요일', '수요일', '목요일', '금요일', '토요일'];
const HOURS = Array.from({ length: 24 }, (_, i) => i);

/** 백엔드 행(0=월) → 프론트 행(0=일) 재배열. feGrid[fe] = grid[(fe+6)%7]. */
function remapToFrontend(grid: number[][]): number[][] {
  return Array.from({ length: 7 }, (_, fe) => grid[(fe + 6) % 7] ?? new Array(24).fill(0));
}

/** 백엔드 dayOfWeek(0=월) → 프론트 행 인덱스(0=일). */
const peakFeDay = (beDay: number) => (beDay + 1) % 7;

function cellColor(total: number, max: number): string {
  if (total === 0 || max === 0) return 'bg-slate-200/5 dark:bg-neutral-800/20';
  const ratio = total / max;
  if (ratio < 0.2) return 'bg-pink-500/15 ring-1 ring-pink-500/10 shadow-[0_0_5px_rgba(236,72,153,0.05)]';
  if (ratio < 0.4) return 'bg-pink-500/30 ring-1 ring-pink-500/20 shadow-[0_0_8px_rgba(236,72,153,0.1)]';
  if (ratio < 0.6) return 'bg-pink-500/50 ring-1 ring-pink-500/35 shadow-[0_0_12px_rgba(236,72,153,0.15)]';
  if (ratio < 0.8) return 'bg-purple-500/70 ring-1 ring-purple-500/50 shadow-[0_0_15px_rgba(168,85,247,0.25)]';
  return 'bg-gradient-to-tr from-pink-500 to-purple-600 ring-1 ring-purple-500/60 shadow-[0_0_20px_rgba(168,85,247,0.4)]';
}

interface HoveredCell {
  day: number; // 프론트 행(0=일)
  hour: number;
  total: number;
}

export default function ActivityHeatmap() {
  const { setActiveTab } = useNavigation();
  const heatmap = useHeatmap();
  const [hoveredCell, setHoveredCell] = useState<HoveredCell | null>(null);

  const feGrid = useMemo(
    () => (heatmap.value ? remapToFrontend(heatmap.value.grid) : null),
    [heatmap.value],
  );
  const max = useMemo(
    () => (feGrid ? Math.max(0, ...feGrid.flat()) : 0),
    [feGrid],
  );

  if (heatmap.query.isLoading) {
    return <Center icon={Loader2} spin text="히트맵을 불러오는 중…" />;
  }
  if (errorCode(heatmap.query.error) === 'IMPORT_NOT_COMPLETED' || !feGrid || !heatmap.value) {
    return (
      <button
        onClick={() => setActiveTab('uploader')}
        className="w-full rounded-3xl border border-dashed border-pink-500/40 bg-pink-500/5 p-12 flex flex-col items-center gap-4 hover:bg-pink-500/10 transition-all"
      >
        <UploadCloud className="w-12 h-12 text-pink-500" />
        <h3 className="text-base font-bold text-slate-800 dark:text-white">먼저 데이터를 임포트하세요</h3>
        <p className="text-xs text-slate-500">임포트가 완료되면 활동 히트맵이 표시됩니다.</p>
      </button>
    );
  }

  const peak = heatmap.value.peak;
  const peakDay = peakFeDay(peak.dayOfWeek);

  return (
    <div className="space-y-8 animate-fade-in">
      <ViewHeader
        icon={Calendar}
        title="시간대별 활동 히트맵"
        subtitle="게시물·좋아요·댓글·DM·로그인을 요일 × 24시간 도표로 정규화해 시각화합니다."
      />

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
        {/* Heatmap grid */}
        <div className="lg:col-span-3 bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark rounded-2xl p-6 flex flex-col relative overflow-x-auto min-w-[700px]">
          <div className="flex mb-2">
            <span className="w-20 shrink-0 select-none text-[10px] text-slate-400">HOUR / DAY</span>
            <div className="flex-1 flex justify-between px-1">
              {HOURS.map((h) => (
                <span key={h} className="w-6 text-center text-[9px] text-slate-400 shrink-0">
                  {h % 4 === 0 ? h : ''}
                </span>
              ))}
            </div>
          </div>

          <div className="space-y-2 select-none">
            {DAYS.map((dayName, dIdx) => (
              <div key={dayName} className="flex items-center">
                <span className="w-20 shrink-0 font-medium text-xs text-slate-700 dark:text-slate-300">
                  {dayName}
                </span>
                <div className="flex-1 flex justify-between gap-1 px-1">
                  {HOURS.map((hour) => {
                    const total = feGrid[dIdx][hour] ?? 0;
                    const isHovered = hoveredCell?.day === dIdx && hoveredCell?.hour === hour;
                    return (
                      <div
                        key={hour}
                        onMouseEnter={() => setHoveredCell({ day: dIdx, hour, total })}
                        onMouseLeave={() => setHoveredCell(null)}
                        className={cn(
                          'w-full max-w-6 aspect-square rounded-md transition-all duration-150 cursor-pointer',
                          cellColor(total, max),
                          isHovered && 'scale-125 ring-2 ring-white z-10 shadow-lg',
                        )}
                      />
                    );
                  })}
                </div>
              </div>
            ))}
          </div>

          <div className="flex justify-between mt-4 text-[9px] text-slate-400 border-t border-slate-200/20 pt-3">
            <span>00:00 (자정)</span>
            <span>06:00 (아침)</span>
            <span>12:00 (정오)</span>
            <span>18:00 (저녁)</span>
            <span>23:00 (심야)</span>
          </div>
        </div>

        {/* Side panel */}
        <div className="lg:col-span-1 space-y-6">
          <div className="p-5 bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark rounded-2xl shadow-sm flex items-stretch gap-3">
            <div className="w-1 rounded-full bg-pink-500 shrink-0" />
            <div className="space-y-1">
              <h4 className="text-xl font-extrabold text-slate-800 dark:text-white leading-tight">
                {DAYS[peakDay]} {formatHourLabel(peak.hour)}
              </h4>
              <p className="text-xs text-slate-500 dark:text-slate-400">
                활동이 가장 활발했던 피크 시점입니다. (기록 {peak.count.toLocaleString()}회)
              </p>
            </div>
          </div>

          <div className="p-5 bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark rounded-2xl shadow-sm min-h-[200px] flex flex-col justify-between">
            <div className="flex items-center gap-2 border-b border-ig-border dark:border-ig-border-dark pb-2.5 mb-2">
              <Clock className="w-4 h-4 text-pink-500" />
              <span className="text-xs font-semibold text-slate-600 dark:text-slate-300">셀 세부 교류량</span>
            </div>

            {hoveredCell ? (
              <div className="space-y-3 flex-1 pt-1.5">
                <div className="space-y-0.5">
                  <h4 className="text-sm font-bold text-slate-800 dark:text-white">{DAYS[hoveredCell.day]}</h4>
                  <p className="text-xs text-pink-500 font-bold">{formatHourLabel(hoveredCell.hour)}</p>
                </div>
                <div className="flex justify-between text-slate-800 dark:text-white border-t border-ig-border dark:border-ig-border-dark pt-1.5 font-sans font-bold text-xs">
                  <span>총합 활동:</span>
                  <span className="text-pink-500">{hoveredCell.total.toLocaleString()}회</span>
                </div>
              </div>
            ) : (
              <div className="flex-1 flex flex-col items-center justify-center text-center p-4">
                <Smile className="w-8 h-8 text-slate-300 dark:text-slate-700 mb-2 animate-bounce" />
                <p className="text-[10px] text-slate-400 dark:text-slate-500">
                  히트맵 칸 위에 마우스를 올리면 해당 요일·시간대의 총 활동량이 표시됩니다.
                </p>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
}

function Center({ icon: Icon, text, spin }: { icon: typeof Loader2; text: string; spin?: boolean }) {
  return (
    <div className="flex flex-col items-center justify-center p-20 gap-3 text-slate-400">
      <Icon className={spin ? 'w-10 h-10 animate-spin' : 'w-10 h-10'} />
      <p className="text-sm">{text}</p>
    </div>
  );
}
