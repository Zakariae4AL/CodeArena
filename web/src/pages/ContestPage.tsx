import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { Countdown } from '../components/Countdown';
import { Page } from '../components/Layout';
import { ScoreboardTable } from '../components/ScoreboardTable';
import { DifficultyTag, ErrorBox, Loading } from '../components/ui';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { useI18n } from '../lib/i18n';
import { useLiveConnected, useTopic } from '../lib/live';
import type { Contest, Scoreboard } from '../lib/types';
import { phaseClass } from './Contests';

export function ContestPage() {
  const { slug } = useParams();
  const { t } = useI18n();
  const { user } = useAuth();
  const queryClient = useQueryClient();
  const contest = useQuery({ queryKey: ['contest', slug], queryFn: () => api<Contest>(`/contests/${slug}`) });
  const board = useQuery({
    queryKey: ['scoreboard', slug],
    queryFn: () => api<Scoreboard>(`/contests/${slug}/scoreboard`),
    enabled: contest.data !== undefined && contest.data.phase !== 'UPCOMING',
  });
  const connected = useLiveConnected();
  const liveBoard = useTopic<Scoreboard>(contest.data ? `/topic/contests/${contest.data.id}/scoreboard` : null);
  const scoreboard = liveBoard ?? board.data;

  if (contest.isPending) return <Loading />;
  if (contest.error)
    return (
      <Page>
        <ErrorBox error={contest.error} />
      </Page>
    );
  const c = contest.data!;
  const refresh = () => void queryClient.invalidateQueries({ queryKey: ['contest', slug] });

  return (
    <Page>
      <div className="card mb-6 overflow-hidden">
        <div className="flex flex-wrap items-center justify-between gap-4 bg-gradient-to-r from-brand-soft to-transparent p-6">
          <div>
            <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${phaseClass[c.phase]}`}>
              {t(`phase.${c.phase}`)}
            </span>
            <h1 className="mt-2 text-2xl font-bold tracking-tight">{c.title}</h1>
            {c.description && <p className="mt-1 max-w-2xl text-sm text-muted">{c.description}</p>}
          </div>
          <div className="text-right">
            <div className="text-xs uppercase tracking-wide text-muted">
              {t(
                c.phase === 'UPCOMING'
                  ? 'contest.startsIn'
                  : c.phase === 'RUNNING'
                    ? 'contest.endsIn'
                    : 'contest.ended',
              )}
            </div>
            {c.phase !== 'ENDED' && (
              <div className="text-3xl font-bold">
                <Countdown
                  target={c.phase === 'UPCOMING' ? c.startsAt : c.endsAt}
                  serverTime={c.serverTime}
                  onDone={refresh}
                />
              </div>
            )}
          </div>
        </div>
      </div>

      <div className="grid grid-cols-[minmax(0,1fr)] gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,2fr)]">
        <section>
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted">{t('contest.problems')}</h2>
          {c.problems.length === 0 ? (
            <p className="card p-5 text-sm text-muted">{t('contest.hidden')}</p>
          ) : (
            <ul className="card divide-y divide-line">
              {c.problems.map((p) => (
                <li key={p.label}>
                  <Link
                    to={`/contests/${c.slug}/problems/${p.label}`}
                    className="flex items-center gap-3 px-4 py-3 hover:bg-surface-2"
                  >
                    <span className="grid h-8 w-8 place-items-center rounded-lg bg-surface-2 font-mono font-bold">
                      {p.label}
                    </span>
                    <span className="flex-1 font-medium">{p.title}</span>
                    <DifficultyTag difficulty={p.difficulty} />
                  </Link>
                </li>
              ))}
            </ul>
          )}
          <p className="mt-3 text-xs text-muted">{t('contest.rules')}</p>
        </section>

        <section>
          <div className="mb-3 flex items-center gap-2">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">{t('contest.scoreboard')}</h2>
            {connected && c.phase === 'RUNNING' && (
              <span className="inline-flex items-center gap-1 rounded-full bg-bad-soft px-2 py-0.5 text-[10px] font-bold uppercase text-bad">
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-bad" /> {t('board.live')}
              </span>
            )}
          </div>
          <div className="card">
            {c.phase === 'UPCOMING' ? (
              <p className="px-4 py-8 text-center text-sm text-muted">{t('contest.hidden')}</p>
            ) : scoreboard ? (
              <ScoreboardTable board={scoreboard} me={user?.id} />
            ) : (
              <Loading />
            )}
          </div>
        </section>
      </div>
    </Page>
  );
}
