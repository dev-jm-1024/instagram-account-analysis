import type { ReactNode } from 'react';
import { cn } from '@/lib/cn';

export interface PillTab<T extends string> {
  id: T;
  label: ReactNode;
}

interface PillTabsProps<T extends string> {
  tabs: PillTab<T>[];
  active: T;
  onChange: (id: T) => void;
  /** Active-pill style: gradient (default) or solid pink. */
  variant?: 'gradient' | 'solid';
  className?: string;
}

/** Segmented pill switcher used by the activity / logs / heatmap views. */
export function PillTabs<T extends string>({
  tabs,
  active,
  onChange,
  variant = 'gradient',
  className,
}: PillTabsProps<T>) {
  const activeClass =
    variant === 'gradient'
      ? 'ig-gradient text-white'
      : 'bg-pink-500 text-white';

  return (
    <div
      className={cn(
        'inline-flex items-center gap-1 p-1 bg-ig-field dark:bg-ig-field-dark',
        'rounded-xl shrink-0',
        className,
      )}
    >
      {tabs.map((tab) => (
        <button
          key={tab.id}
          onClick={() => onChange(tab.id)}
          className={cn(
            'px-3 py-1.5 text-xs font-semibold rounded-lg transition-all',
            active === tab.id
              ? activeClass
              : 'text-ig-muted hover:text-ig-text dark:hover:text-white',
          )}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}
