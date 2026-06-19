/**
 * Date / time formatting helpers shared across views. Centralizing these keeps
 * formatting consistent and removes the per-component `formatDate` duplicates.
 */

const pad = (n: number) => n.toString().padStart(2, '0');

function isValid(ms: number): boolean {
  return !!ms && !isNaN(ms);
}

/** "2026년 6월 4일" */
export function formatKoreanDate(ms: number, fallback = '알 수 없는 시점'): string {
  if (!isValid(ms)) return fallback;
  const d = new Date(ms);
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일`;
}

/** "2026년 6월 4일 14:05" */
export function formatKoreanDateTime(ms: number, fallback = '날짜 정보 없음'): string {
  if (!isValid(ms)) return fallback;
  const d = new Date(ms);
  return `${d.getFullYear()}년 ${d.getMonth() + 1}월 ${d.getDate()}일 ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** "2026-06-04 14:05" */
export function formatTimestamp(ms: number, fallback = '알 수 없음'): string {
  if (!isValid(ms)) return fallback;
  const d = new Date(ms);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/** "오후 02:05" */
export function formatTime(ms: number, fallback = ''): string {
  if (!isValid(ms)) return fallback;
  const d = new Date(ms);
  const hrs = d.getHours();
  const ampm = hrs >= 12 ? '오후' : '오전';
  return `${ampm} ${pad(hrs % 12 || 12)}:${pad(d.getMinutes())}`;
}

/** "14:00" */
export function formatHourLabel(hour: number): string {
  return `${pad(hour)}:00`;
}

/** "14:00 ~ 15:00" */
export function formatHourRange(hour: number): string {
  return `${pad(hour)}:00 ~ ${pad(hour + 1)}:00`;
}
