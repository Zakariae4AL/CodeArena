import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { Countdown } from '../components/Countdown';
import { Page } from '../components/Layout';
import { Empty, ErrorBox, Loading, PageHeader } from '../components/ui';
import { api } from '../lib/api';
import { formatDate } from '../lib/format';
import { useI18n } from '../lib/i18n';
import type { ContestRow, Phase } from '../lib/types';

export const phaseClass: Record<Phase, string> = {
  RUNNING: 'bg-brand text-brand-ink',
  UPCOMING: 'bg-surface-2 text-text',
  ENDED: 'bg-surface-2 text-muted',
};

export function Contests() {
  const { t, locale } = useI18n();
  const contests = useQuery({ queryKey: ['contests'], queryFn: () => api<ContestRow[]>('/contests') });
  const order: Phase[] = ['RUNNING', 'UPCOMING', 'ENDED'];
  const sorted = [...(contests.data ?? [])].sort((a, b) => order.indexOf(a.phase) - order.indexOf(b.phase));

  return (
    <Page>
      <PageHeader title={t('contests.title')} subtitle={t('contests.subtitle')} />
      {contests.isPending ? (
        <Loading />
      ) : contests.error ? (
        <ErrorBox error={contests.error} />
      ) : sorted.length === 0 ? (
        <Empty>{t('contests.empty')}</Empty>
      ) : (
        <ul className="grid gap-4 md:grid-cols-2">
          {sorted.map((c) => (
            <li key={c.id}>
              <Link to={`/contests/${c.slug}`} className="card block p-5 transition hover:border-brand">
                <div className="flex items-start justify-between gap-3">
                  <h2 className="font-semibold">{c.title}</h2>
                  <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${phaseClass[c.phase]}`}>
                    {t(`phase.${c.phase}`)}
                  </span>
                </div>
                <p className="mt-2 text-sm text-muted">
                  {formatDate(c.startsAt, locale)} · {t('contest.problemCount', { n: c.problems })}
                </p>
                {c.phase !== 'ENDED' && (
                  <p className="mt-3 text-sm">
                    <span className="text-muted">
                      {t(c.phase === 'RUNNING' ? 'contest.endsIn' : 'contest.startsIn')}{' '}
                    </span>
                    <Countdown target={c.phase === 'RUNNING' ? c.endsAt : c.startsAt} />
                  </p>
                )}
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Page>
  );
}
