interface RadialProgressProps {
  /** 0–100. */
  percentage: number;
  value: string;
  label?: string;
}

const RADIUS = 40;
const CIRCUMFERENCE = 2 * Math.PI * RADIUS; // ≈ 251.2

/** SVG radial progress ring (e.g. mutual-follow ratio). */
export function RadialProgress({ percentage, value, label }: RadialProgressProps) {
  const offset = CIRCUMFERENCE - (CIRCUMFERENCE * percentage) / 100;

  return (
    <div className="relative w-36 h-36 flex items-center justify-center">
      <svg className="w-full h-full transform -rotate-90" viewBox="0 0 100 100">
        <circle
          cx="50"
          cy="50"
          r={RADIUS}
          className="stroke-neutral-200 dark:stroke-neutral-800"
          strokeWidth="10"
          fill="transparent"
        />
        <circle
          cx="50"
          cy="50"
          r={RADIUS}
          className="stroke-pink-500"
          strokeWidth="10"
          fill="transparent"
          strokeDasharray={CIRCUMFERENCE}
          strokeDashoffset={offset}
          strokeLinecap="round"
          style={{ transition: 'stroke-dashoffset 1s ease-in-out' }}
        />
      </svg>
      <div className="absolute text-center">
        <span className="text-3xl font-extrabold text-slate-900 dark:text-white font-sans">
          {value}
        </span>
        {label && <p className="text-[10px] font-medium text-slate-400 uppercase">{label}</p>}
      </div>
    </div>
  );
}
