import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { Page } from '../../components/Layout';
import { DifficultyTag, ErrorBox, Loading, PageHeader } from '../../components/ui';
import { api } from '../../lib/api';
import { formatDate } from '../../lib/format';
import { useI18n } from '../../lib/i18n';
import type { AdminContest, AdminProblemRow } from '../../lib/types';

export function AdminHome() {
  const { t, locale } = useI18n();
  const problems = useQuery({
    queryKey: ['admin', 'problems'],
    queryFn: () => api<AdminProblemRow[]>('/admin/problems'),
  });
  const contests = useQuery({ queryKey: ['admin', 'contests'], queryFn: () => api<AdminContest[]>('/admin/contests') });

  return (
    <Page>
      <PageHeader title={t('admin.title')} />
      <div className="grid grid-cols-[minmax(0,1fr)] gap-6 lg:grid-cols-2">
        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">{t('admin.problems')}</h2>
            <Link to="/admin/problems/new" className="btn btn-primary py-1.5">
              + {t('admin.newProblem')}
            </Link>
          </div>
          {problems.isPending ? (
            <Loading />
          ) : problems.error ? (
            <ErrorBox error={problems.error} />
          ) : (
            <ul className="card divide-y divide-line">
              {problems.data.map((p) => (
                <li key={p.id}>
                  <Link to={`/admin/problems/${p.id}`} className="flex items-center gap-3 px-4 py-3 hover:bg-surface-2">
                    <span className="flex-1 font-medium">{p.title}</span>
                    <DifficultyTag difficulty={p.difficulty} />
                    <span className="text-xs text-muted">{t('admin.tests', { n: p.tests })}</span>
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-semibold ${
                        p.published ? 'bg-ok-soft text-ok' : 'bg-surface-2 text-muted'
                      }`}
                    >
                      {t(p.published ? 'admin.published' : 'admin.draft')}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">{t('admin.contests')}</h2>
            <Link to="/admin/contests/new" className="btn btn-primary py-1.5">
              + {t('admin.newContest')}
            </Link>
          </div>
          {contests.isPending ? (
            <Loading />
          ) : contests.error ? (
            <ErrorBox error={contests.error} />
          ) : (
            <ul className="card divide-y divide-line">
              {contests.data.map((c) => (
                <li key={c.id}>
                  <Link to={`/admin/contests/${c.id}`} className="flex items-center gap-3 px-4 py-3 hover:bg-surface-2">
                    <span className="flex-1 font-medium">{c.title}</span>
                    <span className="text-xs text-muted">{formatDate(c.startsAt, locale)}</span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </Page>
  );
}
