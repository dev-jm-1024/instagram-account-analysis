import { cn } from '@/lib/cn';

interface ChartTooltipProps {
  /** Injected by Recharts. */
  active?: boolean;
  /** Injected by Recharts. */
  payload?: Array<{ value?: number | string }>;
  /** Injected by Recharts. */
  label?: string | number;
  /** Suffix after the value, e.g. "건" / "개". */
  unit?: string;
  /** Color class for the value, e.g. "text-pink-500". */
  accentClassName?: string;
  /** Format the x-axis label shown in the tooltip header. */
  labelFormatter?: (label: string | number) => string;
}

/** Shared dark-mode-aware tooltip card for Recharts charts (Instagram flat style). */
export function ChartTooltip({
  active,
  payload,
  label,
  unit = '',
  accentClassName = 'text-pink-500',
  labelFormatter,
}: ChartTooltipProps) {
  if (!active || !payload?.length) return null;
  const value = Number(payload[0]?.value ?? 0);
  const labelText = label != null && labelFormatter ? labelFormatter(label) : label;
  return (
    <div className="rounded-lg border border-ig-border dark:border-ig-border-dark bg-ig-surface dark:bg-ig-surface-dark px-3 py-2 shadow-sm">
      <p className="text-[11px] font-semibold text-slate-500 dark:text-slate-400">{labelText}</p>
      <p className={cn('text-sm font-bold', accentClassName)}>
        {value.toLocaleString()}
        {unit}
      </p>
    </div>
  );
}
