import { Search, type LucideIcon } from 'lucide-react';
import { cn } from '@/lib/cn';

interface SearchInputProps {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
  /** Leading icon (default magnifier); pass null to omit. */
  icon?: LucideIcon | null;
  /** Classes for the wrapper element. */
  className?: string;
  /** Extra classes for the input itself. */
  inputClassName?: string;
}

/** Text filter input with an optional leading icon. */
export function SearchInput({
  value,
  onChange,
  placeholder,
  icon = Search,
  className,
  inputClassName,
}: SearchInputProps) {
  const Icon = icon;
  return (
    <div className={cn('relative', className)}>
      <input
        type="text"
        placeholder={placeholder}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        className={cn(
          'w-full py-2 bg-ig-field dark:bg-ig-field-dark border border-transparent',
          'rounded-lg text-sm placeholder-ig-muted text-ig-text dark:text-white',
          'focus:outline-none focus:ring-1 focus:ring-pink-500 focus:border-pink-500',
          Icon ? 'pl-10 pr-4' : 'px-4',
          inputClassName,
        )}
      />
      {Icon && <Icon className="w-4 h-4 text-ig-muted absolute left-3.5 top-1/2 -translate-y-1/2" />}
    </div>
  );
}
