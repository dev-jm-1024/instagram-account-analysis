import type { LucideIcon } from 'lucide-react';
import { motion } from 'motion/react';
import { cn } from '@/lib/cn';

interface StatCardProps {
  label: string;
  title: string;
  sub: string;
  icon: LucideIcon;
  /** Gradient classes for the icon chip, e.g. "from-pink-500/20 to-rose-500/20". */
  iconWrapClassName: string;
  /** Ambient glow dot color, e.g. "bg-pink-500". */
  glowClassName: string;
  /** Stagger index for the entrance animation. */
  index?: number;
  onClick?: () => void;
}

/** Glassy metric card with hover lift and ambient glow (Overview grid). */
export function StatCard({
  label,
  title,
  sub,
  icon: Icon,
  iconWrapClassName,
  glowClassName,
  index = 0,
  onClick,
}: StatCardProps) {
  return (
    <motion.div
      initial={{ opacity: 0, y: 15 }}
      animate={{ opacity: 1, y: 0 }}
      transition={{ delay: index * 0.08 }}
      onClick={onClick}
      className={cn(
        'group rounded-2xl bg-ig-surface dark:bg-ig-surface-dark border border-ig-border dark:border-ig-border-dark p-6',
        'transition-all duration-300 relative overflow-hidden',
        'hover:border-pink-500/40 hover:shadow-sm',
        onClick && 'cursor-pointer',
      )}
    >
      <div className="flex justify-between items-start mb-4">
        <span className="text-xs font-semibold text-ig-muted uppercase tracking-wider">
          {label}
        </span>
        <div
          className={cn(
            'p-2 rounded-xl bg-gradient-to-br border border-white/10 text-slate-700 dark:text-slate-200 shadow-inner',
            iconWrapClassName,
          )}
        >
          <Icon className="w-4 h-4" />
        </div>
      </div>

      <div className="space-y-1">
        <h3 className="text-2xl font-bold text-ig-text dark:text-white tracking-tight">{title}</h3>
        <p className="text-xs text-ig-muted tracking-wide font-sans">{sub}</p>
      </div>

      <div
        className={cn(
          'absolute bottom-3 right-4 w-2 h-2 rounded-full opacity-60 group-hover:scale-125 transition-transform',
          glowClassName,
        )}
      />
    </motion.div>
  );
}
