import { useState } from 'react';
import { Users, ExternalLink, Compass, Loader2 } from 'lucide-react';
import { motion, AnimatePresence } from 'motion/react';
import { useFollows } from '@/services/api/hooks';
import type { FollowItem, FollowQueryType } from '@/services/api/types';
import { formatKoreanDate } from '@/lib/format';
import { cn } from '@/lib/cn';
import { ViewHeader, SearchInput, EmptyState } from '@/components/ui';

type Mode =
  | 'allFollowers'
  | 'allFollowing'
  | 'dontFollowBack'
  | 'dontFollowMe'
  | 'mutual'
  | 'closeFriends'
  | 'unfollowed'
  | 'pending'
  | 'restricted';

const MODE_SPECS: { id: Mode; type: FollowQueryType; label: string; sub: string; color: string }[] = [
  { id: 'allFollowers', type: 'FOLLOWERS', label: '팔로워', sub: '나를 팔로우하는 전체 계정', color: 'text-pink-500 bg-pink-500/10' },
  { id: 'allFollowing', type: 'FOLLOWING', label: '팔로잉', sub: '내가 팔로우하는 전체 계정', color: 'text-sky-500 bg-sky-500/10' },
  { id: 'dontFollowBack', type: 'I_FOLLOW_ONLY', label: '나만 팔로우', sub: '나는 팔로우했는데 나를 팔로우 안한 계정', color: 'text-red-500 bg-red-500/10' },
  { id: 'dontFollowMe', type: 'FOLLOWS_ME_ONLY', label: '나를 팔로우', sub: '상대는 나를 팔로우하나 내가 아직 팔로우 안한 계정', color: 'text-purple-500 bg-purple-500/10' },
  { id: 'mutual', type: 'MUTUAL', label: '맞팔', sub: '서로 맞팔로우 되어있는 계정', color: 'text-emerald-500 bg-emerald-500/10' },
  { id: 'closeFriends', type: 'CLOSE_FRIENDS', label: '친한 친구', sub: '친한친구로 설정되어있는 계정', color: 'text-[#00D53C] bg-[#00D53C]/10' },
  { id: 'unfollowed', type: 'UNFOLLOWED', label: '최근 언팔', sub: '최근에 팔로우를 끊은 계정들', color: 'text-orange-500 bg-orange-500/10' },
  { id: 'pending', type: 'PENDING', label: '대기 중인 요청', sub: '수락 완료 전, 대기 중인 보낸 요청', color: 'text-blue-500 bg-blue-500/10' },
  { id: 'restricted', type: 'RESTRICTED', label: '제한한 계정', sub: '디엠 및 댓글 피드 노출을 차단한 계정', color: 'text-rose-500 bg-rose-500/10' },
];

export default function Relations() {
  const [activeMode, setActiveMode] = useState<Mode>('allFollowers');
  const [searchQuery, setSearchQuery] = useState('');

  // 7 모드 모두 조회(정적 개수·캐시됨) → 모든 배지 카운트 + 활성 목록.
  const q = {
    allFollowers: useFollows('FOLLOWERS'),
    allFollowing: useFollows('FOLLOWING'),
    dontFollowBack: useFollows('I_FOLLOW_ONLY'),
    dontFollowMe: useFollows('FOLLOWS_ME_ONLY'),
    mutual: useFollows('MUTUAL'),
    closeFriends: useFollows('CLOSE_FRIENDS'),
    unfollowed: useFollows('UNFOLLOWED'),
    pending: useFollows('PENDING'),
    restricted: useFollows('RESTRICTED'),
  } satisfies Record<Mode, ReturnType<typeof useFollows>>;

  const itemsOf = (m: Mode): FollowItem[] => q[m].value?.items ?? [];
  const active = q[activeMode];
  const isCloseFriends = activeMode === 'closeFriends'; // 친한 친구는 #00D53C 로 강조 표시

  const term = searchQuery.trim().toLowerCase();
  const rawItems = active.value?.items ?? [];
  const listToRender = term
    ? rawItems.filter((u) => u.username.toLowerCase().includes(term))
    : rawItems;

  return (
    <div className="space-y-8 animate-fade-in">
      <ViewHeader
        icon={Users}
        title="팔로우 관계 보기"
        subtitle="맞팔 상태, 친한 친구, 제한한 계정 등 다양한 디렉터리를 대조 필터링합니다."
        action={
          <SearchInput
            value={searchQuery}
            onChange={setSearchQuery}
            placeholder="유저네임 검색..."
            className="w-full md:w-80"
          />
        }
      />

      <div className="grid grid-cols-1 lg:grid-cols-4 gap-8">
        {/* Controls Panel */}
        <div className="lg:col-span-1 space-y-2.5">
          {MODE_SPECS.map((spec) => (
            <button
              key={spec.id}
              onClick={() => {
                setActiveMode(spec.id);
                setSearchQuery('');
              }}
              className={cn(
                'w-full text-left p-4 rounded-2xl flex flex-col gap-1 transition-all',
                activeMode === spec.id
                  ? 'bg-ig-surface dark:bg-ig-surface-dark border border-pink-500/40 ring-1 ring-pink-500/20'
                  : 'bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark hover:bg-ig-field dark:hover:bg-ig-field-dark',
              )}
            >
              <div className="flex justify-between items-center w-full">
                <span
                  className={cn(
                    'text-sm font-semibold',
                    activeMode === spec.id
                      ? 'text-pink-500 dark:text-pink-400'
                      : 'text-slate-700 dark:text-slate-300',
                  )}
                >
                  {spec.label}
                </span>
                <span className={cn('text-xs px-2.5 py-1 rounded-full font-bold', spec.color)}>
                  {q[spec.id].query.isLoading ? '…' : itemsOf(spec.id).length}
                </span>
              </div>
              <p className="text-[10px] text-slate-400 dark:text-slate-500 leading-normal line-clamp-1">
                {spec.sub}
              </p>
            </button>
          ))}
        </div>

        {/* List Content */}
        <div className="lg:col-span-3 bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark rounded-2xl p-6 min-h-[500px] flex flex-col relative overflow-hidden">
          {active.query.isLoading ? (
            <EmptyState icon={Loader2} spin title="불러오는 중…" />
          ) : listToRender.length === 0 ? (
            <EmptyState
              icon={Compass}
              spin
              title="검색되거나 존재하는 계정이 없습니다."
              description="현재 카테고리에는 조건에 부합하는 계정이 없습니다."
            />
          ) : (
            <div className="space-y-4">
              <div className="flex justify-between items-center text-[10px] text-slate-400 border-b border-ig-border dark:border-ig-border-dark pb-2 uppercase tracking-wider">
                <span>계정 프로필 유저네임 ({listToRender.length})</span>
                <span>커넥션 기록 시점</span>
              </div>

              <div className="divide-y divide-slate-100/50 dark:divide-white/5 max-h-[580px] overflow-y-auto pr-2 space-y-1.5 pt-1.5">
                <AnimatePresence mode="popLayout">
                  {listToRender.map((node, index) => (
                    <motion.div
                      key={node.username + index}
                      initial={{ opacity: 0, x: -10 }}
                      animate={{ opacity: 1, x: 0 }}
                      exit={{ opacity: 0, x: 10 }}
                      transition={{ duration: 0.15, delay: Math.min(index * 0.02, 0.3) }}
                      className="flex items-center justify-between py-3 px-4 hover:bg-ig-field dark:hover:bg-ig-field-dark rounded-xl transition-all duration-150 group"
                    >
                      <div className="flex items-center gap-3">
                        <div
                          className={cn(
                            'w-10 h-10 rounded-full p-[2px] shadow-md',
                            isCloseFriends ? 'bg-[#00D53C]' : 'bg-gradient-to-tr from-yellow-400 via-pink-500 to-purple-600',
                          )}
                        >
                          <div className="w-full h-full rounded-full bg-slate-100 dark:bg-slate-900 border border-ig-border dark:border-ig-border-dark flex items-center justify-center">
                            <span className="text-xs font-bold text-slate-700 dark:text-slate-300">
                              {node.username.slice(0, 2).toUpperCase()}
                            </span>
                          </div>
                        </div>

                        <div>
                          <p
                            className={cn(
                              'text-sm font-semibold transition-colors',
                              isCloseFriends
                                ? 'text-[#00D53C] hover:text-[#00D53C]/80'
                                : 'text-slate-800 dark:text-white hover:text-pink-500',
                            )}
                          >
                            {node.username}
                          </p>
                          <a
                            href={`https://www.instagram.com/${node.username}`}
                            target="_blank"
                            rel="noopener noreferrer"
                            className="text-[10px] text-slate-400 hover:text-pink-500 flex items-center gap-0.5 mt-0.5 transition-colors"
                          >
                            인스타에서 프로필 보기
                            <ExternalLink className="w-2.5 h-2.5" />
                          </a>
                        </div>
                      </div>

                      <div className="text-right flex items-center gap-4">
                        <span className="text-xs text-slate-400 dark:text-slate-500">
                          {formatKoreanDate(node.followedAt ?? 0)}
                        </span>
                        <a
                          href={`https://www.instagram.com/${node.username}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="p-1 rounded-lg bg-pink-500/10 text-pink-500 border border-pink-500/20 opacity-0 group-hover:opacity-100 transition-all shadow-sm hover:bg-pink-500 hover:text-white"
                        >
                          <ExternalLink className="w-3.5 h-3.5" />
                        </a>
                      </div>
                    </motion.div>
                  ))}
                </AnimatePresence>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
