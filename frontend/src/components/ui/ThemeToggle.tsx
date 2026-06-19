import { Moon, Sun } from 'lucide-react';
import { useTheme } from '@/app/theme/useTheme';
import { cn } from '@/lib/cn';

/** Light/dark theme switch — a sliding glass pill. */
export function ThemeToggle() {
  const { theme, toggleTheme } = useTheme();
  const isDark = theme === 'dark';

  return (
    <button
      type="button"
      role="switch"
      aria-checked={isDark}
      aria-label={isDark ? '라이트 모드로 전환' : '다크 모드로 전환'}
      onClick={toggleTheme}
      className="w-full px-3 py-2 rounded-xl flex items-center justify-between gap-2 border border-ig-border dark:border-ig-border-dark bg-ig-field dark:bg-ig-field-dark hover:bg-black/[0.06] dark:hover:bg-white/10 transition-all group"
    >
      <span className="flex items-center gap-2 text-xs font-medium text-ig-text dark:text-slate-300">
        {isDark ? (
          <Moon className="w-3.5 h-3.5 text-indigo-400" />
        ) : (
          <Sun className="w-3.5 h-3.5 text-amber-500" />
        )}
        {isDark ? '다크 모드' : '라이트 모드'}
      </span>

      {/* Track */}
      <span
        className={cn(
          'relative w-9 h-5 rounded-full transition-colors shrink-0',
          isDark ? 'bg-indigo-500/70' : 'bg-slate-300',
        )}
      >
        <span
          className={cn(
            'absolute top-0.5 left-0.5 w-4 h-4 rounded-full bg-white shadow-sm transition-transform flex items-center justify-center',
            isDark && 'translate-x-4',
          )}
        >
          {isDark ? (
            <Moon className="w-2.5 h-2.5 text-indigo-500" />
          ) : (
            <Sun className="w-2.5 h-2.5 text-amber-500" />
          )}
        </span>
      </span>
    </button>
  );
}
