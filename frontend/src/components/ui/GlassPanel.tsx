import type { HTMLAttributes } from 'react';
import { cn } from '@/lib/cn';

/** The thin gradient highlight strip repeated across every glass surface. */
export function PanelHighlight({ via = 'via-white/30' }: { via?: string }) {
  return (
    <div
      className={cn(
        'absolute top-0 left-0 w-full h-[1px] bg-gradient-to-r from-transparent to-transparent',
        via,
      )}
    />
  );
}

interface GlassPanelProps extends HTMLAttributes<HTMLDivElement> {
  /** Render the top highlight strip (default true). */
  highlight?: boolean;
  /** Tailwind `via-*` color of the highlight strip. */
  highlightVia?: string;
}

/**
 * A frosted "liquid glass" surface. The base look can be overridden via
 * `className` (e.g. a different background opacity) while keeping the shared
 * border / blur / highlight treatment.
 */
export function GlassPanel({
  highlight = false,
  highlightVia,
  className,
  children,
  ...rest
}: GlassPanelProps) {
  return (
    <div
      className={cn(
        'relative overflow-hidden rounded-2xl border border-ig-border dark:border-ig-border-dark',
        'bg-ig-surface dark:bg-ig-surface-dark',
        className,
      )}
      {...rest}
    >
      {highlight && <PanelHighlight via={highlightVia} />}
      {children}
    </div>
  );
}
