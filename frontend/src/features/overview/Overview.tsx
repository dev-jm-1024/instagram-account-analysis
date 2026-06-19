import {
  Users,
  MessageCircle,
  Heart,
  MessageSquare,
  Clock,
  Zap,
  UploadCloud,
  Loader2,
} from 'lucide-react';
import type { ActiveTab } from '@/domain/types';
import { useNavigation } from '@/app/routing/useNavigation';
import { StatCard, RadialProgress } from '@/components/ui';
import { useOverview, useHeatmap, useMessageStats } from '@/services/api/hooks';
import { formatHourRange } from '@/lib/format';

export default function Overview() {
  const { setActiveTab } = useNavigation();
  const overview = useOverview();
  const heatmap = useHeatmap();
  const messages = useMessageStats();

  if (overview.query.isLoading) return <CenterState icon={Loader2} spin text="요약을 불러오는 중…" />;

  const o = overview.value?.data ?? null;
  if (overview.value?.importRequired || !o) {
    return (
      <button
        onClick={() => setActiveTab('uploader')}
        className="w-full rounded-3xl border border-dashed border-pink-500/40 bg-pink-500/5 p-12 flex flex-col items-center gap-4 hover:bg-pink-500/10 transition-all"
      >
        <UploadCloud className="w-12 h-12 text-pink-500" />
        <h3 className="text-base font-bold text-slate-800 dark:text-white">먼저 데이터를 임포트하세요</h3>
        <p className="text-xs text-slate-500 max-w-sm text-center">
          Instagram 내보내기 ZIP 을 업로드하거나 서버의 export 폴더를 임포트하면 이 화면이 채워집니다.
        </p>
      </button>
    );
  }

  const followsMeOnly = Math.max(0, o.followerCount - o.mutualCount);
  const mutualPct = o.followingCount > 0 ? Math.round((o.mutualCount / o.followingCount) * 100) : 0;
  const peakLabel =
    heatmap.value && heatmap.value.peak.count > 0
      ? formatHourRange(heatmap.value.peak.hour)
      : '데이터 부족';
  const topPartner = messages.value?.topPartners[0] ?? null;
  const lifespan = computeLifespan(o.activityFrom ?? null, o.activityTo ?? null);

  const cards: {
    label: string;
    title: string;
    sub: string;
    icon: typeof Users;
    iconWrap: string;
    glow: string;
    tab: ActiveTab;
  }[] = [
    {
      label: '팔로워 관계',
      title: `${o.followerCount}명`,
      sub: `맞팔 ${o.mutualCount}명 · 팔로잉 ${o.iFollowOnlyCount}명`,
      icon: Users,
      iconWrap: 'from-pink-500/20 to-rose-500/20 shadow-pink-500/5',
      glow: 'bg-pink-500',
      tab: 'followers',
    },
    {
      label: '디엠 소통량',
      title: `${o.messageCount.toLocaleString()}개`,
      sub: `${o.conversationCount}개 대화방`,
      icon: MessageCircle,
      iconWrap: 'from-purple-500/20 to-indigo-500/20 shadow-purple-500/5',
      glow: 'bg-purple-500',
      tab: 'dm',
    },
    {
      label: '댓글 및 좋아요',
      title: `${(o.likeCount + o.commentCount).toLocaleString()}회`,
      sub: `좋아요 ${o.likeCount.toLocaleString()}개 · 댓글 ${o.commentCount.toLocaleString()}개`,
      icon: Heart,
      iconWrap: 'from-amber-500/20 to-orange-500/20 shadow-amber-500/5',
      glow: 'bg-amber-500',
      tab: 'activity',
    },
    {
      label: '게시물 및 미디어',
      title: `${o.totalPostCount}개`,
      sub: '스토리/릴스 및 포스트 백업',
      icon: MessageSquare,
      iconWrap: 'from-blue-500/20 to-cyan-500/20 shadow-blue-500/5',
      glow: 'bg-blue-500',
      tab: 'activity',
    },
  ];

  return (
    <div className="space-y-8 animate-fade-in">
      {/* Welcome Banner */}
      <div className="relative overflow-hidden rounded-3xl bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark p-8 md:p-10 flex flex-col md:flex-row items-center justify-between gap-6">
        <div className="space-y-3 max-w-xl text-center md:text-left z-10">
          <div className="inline-flex items-center px-3 py-1.5 rounded-full bg-pink-500/10 border border-pink-500/30 text-pink-600 dark:text-pink-300 text-xs font-semibold uppercase tracking-wider">
            인스턴트 요약 및 통계
          </div>
          <h2 className="text-2xl md:text-3xl font-bold text-ig-text dark:text-white leading-tight font-sans">
            인스타그램 데이터 한눈에 보기
          </h2>
          <p className="text-slate-600 dark:text-slate-300 text-sm leading-relaxed">
            Meta 내 정보 다운로드 백업을 서버에서 파싱·집계해 통계로 보여줍니다.
            {o.mostActiveMonth && (
              <>
                {' '}가장 활발했던 달은 <strong>{o.mostActiveMonth}</strong> 입니다.
              </>
            )}
          </p>
        </div>

        <div className="bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark rounded-2xl p-5 w-full md:w-64 space-y-3 z-10">
          <div className="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
            <Clock className="w-4 h-4 text-pink-500" />
            <span>계정 활동 연령</span>
          </div>
          <div className="flex items-baseline gap-1">
            <span className="text-3xl font-extrabold text-slate-900 dark:text-white font-sans">
              {lifespan.years}
            </span>
            <span className="text-sm font-medium text-slate-500 dark:text-slate-400">년 간</span>
          </div>
          <p className="text-[11px] text-slate-400 dark:text-slate-500">
            {lifespan.days.toLocaleString()}일의 통계 로그가 발견되었습니다.
          </p>
        </div>
      </div>

      {/* Grid of 4 Key Glass Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6">
        {cards.map((card, idx) => (
          <StatCard
            key={card.label}
            label={card.label}
            title={card.title}
            sub={card.sub}
            icon={card.icon}
            iconWrapClassName={card.iconWrap}
            glowClassName={card.glow}
            index={idx}
            onClick={() => setActiveTab(card.tab)}
          />
        ))}
      </div>

      {/* Key Insights Bento Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 rounded-2xl bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark p-6 shadow-sm flex flex-col justify-between relative">
          <div className="mb-6">
            <h3 className="text-base font-bold text-slate-900 dark:text-white flex items-center gap-2">
              <Zap className="w-4 h-4 text-violet-500" />
              맞팔 관계 요약
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
              서로 팔로우 / 나만 팔로우 / 나를 팔로우 비율을 보여줍니다.
            </p>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-6 items-center">
            <div className="flex flex-col items-center justify-center py-4 bg-slate-500/5 rounded-2xl relative border border-slate-200/20">
              <div className="absolute top-2 right-2 text-[10px] text-slate-400">MUTUAL RATIO</div>
              <RadialProgress percentage={mutualPct} value={`${mutualPct}%`} label="맞팔률" />
            </div>

            <div className="space-y-4">
              <div className="p-3 bg-ig-surface dark:bg-ig-surface-dark rounded-xl border border-ig-border dark:border-ig-border-dark">
                <span className="text-[11px] font-semibold text-slate-400 dark:text-slate-500 block uppercase">
                  나만 팔로우
                </span>
                <div className="flex justify-between items-baseline mt-1">
                  <span className="text-xl font-bold text-pink-500 dark:text-pink-400">
                    {o.iFollowOnlyCount}명
                  </span>
                  <span className="text-xs text-slate-400">내가 팔로우 중이나 상대가 나를 팔로우 안함</span>
                </div>
              </div>

              <div className="p-3 bg-ig-surface dark:bg-ig-surface-dark rounded-xl border border-ig-border dark:border-ig-border-dark">
                <span className="text-[11px] font-semibold text-slate-400 dark:text-slate-500 block uppercase">
                  나를 팔로우
                </span>
                <div className="flex justify-between items-baseline mt-1">
                  <span className="text-xl font-bold text-purple-500 dark:text-purple-400">
                    {followsMeOnly}명
                  </span>
                  <span className="text-xs text-slate-400">상대가 나를 팔로우 중이나 내가 팔로우 안함</span>
                </div>
              </div>

              <p className="text-[11px] text-slate-400 dark:text-slate-500 italic">
                * tip: 팔로우 탭에서 맞팔하지 않는 짝사랑 친구들을 전수 조회할 수 있습니다.
              </p>
            </div>
          </div>
        </div>

        <div className="space-y-6 flex flex-col justify-between">
          <div className="rounded-2xl bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark p-6 shadow-sm flex-1 flex flex-col justify-between">
            <div className="flex justify-between items-center">
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-widest">Golden Hour</span>
              <Clock className="w-4 h-4 text-amber-500" />
            </div>
            <div className="my-3">
              <h4 className="text-sm font-medium text-slate-400 dark:text-slate-500">최대 활성 시간대</h4>
              <p className="text-lg font-bold text-slate-900 dark:text-white mt-1 leading-snug">{peakLabel}</p>
            </div>
            <span className="text-[11px] text-slate-400 dark:text-slate-500">
              게시물·좋아요·댓글·DM·로그인 통계를 취합했을 때 가장 활동이 활발한 시간대입니다.
            </span>
          </div>

          <div className="rounded-2xl bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark p-6 shadow-sm flex-1 flex flex-col justify-between">
            <div className="flex justify-between items-center">
              <span className="text-xs font-semibold text-slate-400 uppercase tracking-widest">Soul Partner</span>
              <MessageCircle className="w-4 h-4 text-purple-500" />
            </div>
            <div className="my-3">
              <h4 className="text-sm font-medium text-slate-400 dark:text-slate-500">인스타 소울메이트</h4>
              <p className="text-lg font-bold text-slate-900 dark:text-white mt-1 leading-snug">
                {topPartner
                  ? `${topPartner.partnerName} (총 ${topPartner.messageCount.toLocaleString()}대화)`
                  : messages.query.isError
                    ? '본인 계정 확정 필요'
                    : '데이터 없음'}
              </p>
            </div>
            <span className="text-[11px] text-slate-400 dark:text-slate-500">
              디엠 대화를 가장 많이 주고받은 대화 상대 1위 계정입니다.
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}

function computeLifespan(from: number | null, to: number | null) {
  if (!from || !to || to <= from) return { years: 0, days: 0 };
  const days = Math.round((to - from) / 86_400_000);
  const years = +(days / 365.25).toFixed(1);
  return { years, days };
}

function CenterState({
  icon: Icon,
  text,
  spin,
}: {
  icon: typeof Loader2;
  text: string;
  spin?: boolean;
}) {
  return (
    <div className="flex flex-col items-center justify-center p-20 gap-3 text-slate-400">
      <Icon className={spin ? 'w-10 h-10 animate-spin' : 'w-10 h-10'} />
      <p className="text-sm">{text}</p>
    </div>
  );
}
