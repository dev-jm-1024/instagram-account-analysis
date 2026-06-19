import type { LucideIcon } from 'lucide-react';
import { cn } from '@/lib/cn';

interface EmptyStateProps {
  icon: LucideIcon;
  title: string;
  description?: string;
  /** Slowly spin the icon (used for the "searching" empty states). */
  spin?: boolean;
  className?: string;
}

/** Centered placeholder shown when a list has no items to display. */
export function EmptyState({ icon: Icon, title, description, spin, className }: EmptyStateProps) {
  return (
    <div className={cn('flex-1 flex flex-col items-center justify-center text-center p-12', className)}>
      <Icon
        className={cn(
          'w-12 h-12 text-slate-300 dark:text-slate-700 mb-4',
          spin && 'animate-spin-slow',
        )}
      />
      <h4 className="text-sm font-semibold text-slate-700 dark:text-slate-300">{title}</h4>
      {description && (
        <p className="text-xs text-slate-400 dark:text-slate-500 max-w-xs mt-2">{description}</p>
      )}
    </div>
  );
}
