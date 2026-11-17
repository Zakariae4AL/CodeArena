import type { Verdict } from './types';

export const verdictShort: Record<Verdict, string> = {
  ACCEPTED: 'AC',
  WRONG_ANSWER: 'WA',
  TIME_LIMIT_EXCEEDED: 'TLE',
  MEMORY_LIMIT_EXCEEDED: 'MLE',
  OUTPUT_LIMIT_EXCEEDED: 'OLE',
  RUNTIME_ERROR: 'RE',
  COMPILATION_ERROR: 'CE',
  SYSTEM_ERROR: 'SE',
};

export function verdictTone(verdict: Verdict | null | undefined): 'ok' | 'bad' | 'warn' | 'muted' {
  switch (verdict) {
    case 'ACCEPTED':
      return 'ok';
    case 'TIME_LIMIT_EXCEEDED':
    case 'MEMORY_LIMIT_EXCEEDED':
    case 'OUTPUT_LIMIT_EXCEEDED':
      return 'warn';
    case undefined:
    case null:
    case 'SYSTEM_ERROR':
      return 'muted';
    default:
      return 'bad';
  }
}

export function formatMemory(kb: number): string {
  return kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb} KB`;
}

/** "2d 03:04:05" or "03:04:05". */
export function formatCountdown(ms: number): string {
  const total = Math.max(0, Math.floor(ms / 1000));
  const days = Math.floor(total / 86400);
  const hh = String(Math.floor((total % 86400) / 3600)).padStart(2, '0');
  const mm = String(Math.floor((total % 3600) / 60)).padStart(2, '0');
  const ss = String(total % 60).padStart(2, '0');
  return `${days > 0 ? `${days}d ` : ''}${hh}:${mm}:${ss}`;
}

/** Penalty minutes as h:mm. */
export function formatMinutes(minutes: number): string {
  return `${Math.floor(minutes / 60)}:${String(minutes % 60).padStart(2, '0')}`;
}

export function formatDate(iso: string, locale: string): string {
  return new Intl.DateTimeFormat(locale, { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(iso));
}
