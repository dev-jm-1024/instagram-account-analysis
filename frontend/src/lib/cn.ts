/**
 * Joins class name fragments, dropping falsy values. A tiny dependency-free
 * helper for conditional Tailwind classes:
 *
 *   cn('px-2', isActive && 'bg-pink-500', error ? 'text-red-500' : 'text-slate-500')
 */
export type ClassValue = string | number | false | null | undefined;

export function cn(...values: ClassValue[]): string {
  return values.filter(Boolean).join(' ');
}
