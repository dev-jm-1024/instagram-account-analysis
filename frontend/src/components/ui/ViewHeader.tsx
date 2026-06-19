import type { ReactNode } from 'react';
import type { LucideIcon } from 'lucide-react';
import { cn } from '@/lib/cn';

interface ViewHeaderProps {
  icon: LucideIcon;
  /** Tailwind color class for the icon, e.g. "text-pink-500". */
  iconClassName?: string;
  title: ReactNode;
  subtitle?: ReactNode;
  /** Optional right-aligned controls (search box, tabs, etc.). */
  action?: ReactNode;
  /** Render a bottom divider (default true). */
  divider?: boolean;
}

/** Standard view title block: icon + heading + subtitle, optional right action. */
export function ViewHeader({
  icon: Icon,
  iconClassName = 'text-pink-500',
  title,
  subtitle,
  action,
  divider = true,
}: ViewHeaderProps) {
  return (
    <div
      className={cn(
        'flex flex-col md:flex-row md:items-center justify-between gap-4',
        divider && 'border-b border-ig-border dark:border-ig-border-dark pb-6',
      )}
    >
      <div>
        <h2 className="text-xl font-bold text-ig-text dark:text-white flex items-center gap-2">
          <Icon className={cn('w-5 h-5', iconClassName)} />
          {title}
        </h2>
        {subtitle && <p className="text-xs text-ig-muted mt-1">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}
