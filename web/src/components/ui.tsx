import type { ReactNode } from 'react';
import { useI18n } from '../lib/i18n';
import { verdictShort, verdictTone } from '../lib/format';
import type { Difficulty, Status, Verdict } from '../lib/types';

const toneClass = {
  ok: 'bg-ok-soft text-ok',
  bad: 'bg-bad-soft text-bad',
  warn: 'bg-warn-soft text-warn',
  muted: 'bg-surface-2 text-muted',
} as const;

export function VerdictBadge({
  verdict,
  status,
  short,
}: {
  verdict: Verdict | null;
  status?: Status;
  short?: boolean;
}) {
  const { t } = useI18n();
  if (!verdict) {
    return (
      <span className="inline-flex items-center gap-1.5 rounded-full bg-surface-2 px-2.5 py-0.5 text-xs font-semibold text-muted">
        <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-brand" />
        {t(`status.${status ?? 'QUEUED'}`)}
      </span>
    );
  }
  return (
    <span
      title={t(`verdict.${verdict}`)}
      className={`inline-flex rounded-full px-2.5 py-0.5 text-xs font-semibold ${toneClass[verdictTone(verdict)]}`}
    >
      {short ? verdictShort[verdict] : t(`verdict.${verdict}`)}
    </span>
  );
}

const difficultyClass: Record<Difficulty, string> = {
  EASY: 'text-ok',
  MEDIUM: 'text-warn',
  HARD: 'text-bad',
};

export function DifficultyTag({ difficulty }: { difficulty: Difficulty }) {
  const { t } = useI18n();
  return (
    <span className={`text-xs font-semibold ${difficultyClass[difficulty]}`}>{t(`difficulty.${difficulty}`)}</span>
  );
}

export function PageHeader({ title, subtitle, actions }: { title: string; subtitle?: string; actions?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-muted">{subtitle}</p>}
      </div>
      {actions}
    </div>
  );
}

export function Loading() {
  const { t } = useI18n();
  return <p className="py-10 text-center text-sm text-muted">{t('common.loading')}</p>;
}

export function ErrorBox({ error }: { error: unknown }) {
  const { t } = useI18n();
  return (
    <p role="alert" className="rounded-xl bg-bad-soft px-4 py-3 text-sm text-bad">
      {error instanceof Error ? error.message : t('common.error')}
    </p>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return <div className="card px-6 py-12 text-center text-sm text-muted">{children}</div>;
}

export function Pre({ children, label }: { children: string; label?: string }) {
  return (
    <div>
      {label && <div className="mb-1 text-xs font-semibold uppercase tracking-wide text-muted">{label}</div>}
      <pre className="max-h-56 overflow-auto rounded-lg border border-line bg-surface-2 px-3 py-2 font-mono text-xs leading-relaxed">
        {children || ' '}
      </pre>
    </div>
  );
}
