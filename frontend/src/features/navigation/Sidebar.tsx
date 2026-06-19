import { motion } from 'motion/react';
import { useQuery } from '@tanstack/react-query';
import { useNavigation } from '@/app/routing/useNavigation';
import { api } from '@/services/api/endpoints';
import { queryKeys } from '@/services/api/queryKeys';
import { cn } from '@/lib/cn';
import { ThemeToggle } from '@/components/ui';
import { NAV_SECTIONS } from './navConfig';

export default function Sidebar() {
  const { activeTab, setActiveTab } = useNavigation();
  const importStatus = useQuery({ queryKey: queryKeys.importStatus, queryFn: () => api.importStatus() });
  const live = importStatus.data?.status === 'COMPLETED';

  return (
    <aside
      id="instagram-sidebar"
      className="w-64 shrink-0 h-full bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark rounded-2xl p-6 flex flex-col justify-between z-40 transition-all"
    >
      <div>
        {/* Brand */}
        <div className="flex items-center gap-3 mb-8 px-2 group">
          <img
            src="/logo.svg"
            alt="Instagram Analyze"
            className="w-10 h-10 rounded-xl object-cover shrink-0 group-hover:scale-105 transition-transform"
          />
          <div>
            <h1 className="text-md font-bold text-ig-text dark:text-white leading-tight font-sans tracking-tight">
              Instagram Analyze
            </h1>
            <p className="text-[10px] text-ig-muted tracking-widest uppercase">
              Export Analyzer
            </p>
          </div>
        </div>

        {/* Import status badge — 데이터가 없을 때만 임포트 안내 표시 */}
        {!live && (
          <div className="mb-6 px-2">
            <button
              id="import-status-badge"
              onClick={() => setActiveTab('uploader')}
              className="w-full px-3 py-2 rounded-xl text-xs font-medium flex items-center justify-between transition-all border bg-pink-500/10 border-pink-500/30 text-pink-600 dark:text-pink-300"
            >
              <div className="flex items-center gap-2">
                <span className="w-2 h-2 rounded-full bg-pink-500 animate-pulse" />
                <span>데이터 임포트 필요</span>
              </div>
              <span className="text-[10px] uppercase bg-black/5 dark:bg-white/10 px-1.5 py-0.5 rounded-md">
                IMPORT
              </span>
            </button>
          </div>
        )}

        {/* Nav groups */}
        <nav className="space-y-4 overflow-y-auto max-h-[65vh] pr-1">
          {NAV_SECTIONS.map((section) => (
            <div key={section.group} className="space-y-1">
              <span className="text-[10px] tracking-wider text-ig-muted px-3 uppercase block mb-1">
                {section.group}
              </span>

              {section.items.map((item) => {
                const Icon = item.icon;
                const isActive = activeTab === item.id;
                return (
                  <button
                    key={item.id}
                    onClick={() => setActiveTab(item.id)}
                    className={cn(
                      'w-full px-3 py-[9px] rounded-xl text-xs font-semibold flex items-center gap-3 transition-all text-left relative group',
                      isActive
                        ? 'text-ig-text dark:text-white bg-black/5 dark:bg-white/10'
                        : 'text-ig-muted hover:text-ig-text dark:hover:text-white hover:bg-black/[0.03] dark:hover:bg-white/5',
                    )}
                  >
                    {isActive && (
                      <motion.div
                        layoutId="sidebar-active-indicator"
                        className="absolute left-0 w-1.5 h-6/12 ig-gradient rounded-r-md"
                        transition={{ type: 'spring', stiffness: 350, damping: 30 }}
                      />
                    )}
                    <div
                      className={cn(
                        'p-1 rounded-lg transition-transform group-hover:scale-105',
                        isActive
                          ? 'text-pink-500 mr-0.5'
                          : 'text-ig-muted group-hover:text-ig-text dark:group-hover:text-slate-300',
                      )}
                    >
                      <Icon className="w-4 h-4" />
                    </div>
                    <span className="truncate">{item.label}</span>
                  </button>
                );
              })}
            </div>
          ))}
        </nav>
      </div>

      {/* Footer */}
      <div className="pt-4 border-t border-ig-border dark:border-ig-border-dark">
        <ThemeToggle />
      </div>
    </aside>
  );
}
