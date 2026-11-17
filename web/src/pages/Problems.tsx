import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { Link } from 'react-router';
import { Page } from '../components/Layout';
import { DifficultyTag, Empty, ErrorBox, Loading, PageHeader } from '../components/ui';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { useI18n } from '../lib/i18n';
import type { ProblemSummary } from '../lib/types';

export function Problems() {
  const { t } = useI18n();
  const { user } = useAuth();
  const [search, setSearch] = useState('');
  const problems = useQuery({ queryKey: ['problems'], queryFn: () => api<ProblemSummary[]>('/problems') });
  const solved = useQuery({
    queryKey: ['solved', user?.id],
    queryFn: () => api<number[]>('/submissions/solved'),
    enabled: Boolean(user),
  });
  const solvedSet = useMemo(() => new Set(solved.data ?? []), [solved.data]);
  const visible = (problems.data ?? []).filter((p) => p.title.toLowerCase().includes(search.trim().toLowerCase()));

  return (
    <Page>
      <PageHeader
        title={t('problems.title')}
        subtitle={t('problems.subtitle')}
        actions={
          <input
            className="field w-64"
            type="search"
            placeholder={t('problems.search')}
            aria-label={t('problems.search')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        }
      />
      {problems.isPending ? (
        <Loading />
      ) : problems.error ? (
        <ErrorBox error={problems.error} />
      ) : visible.length === 0 ? (
        <Empty>{t('problems.empty')}</Empty>
      ) : (
        <ul className="card divide-y divide-line">
          {visible.map((p, i) => (
            <li key={p.id}>
              <Link
                to={`/problems/${p.slug}`}
                className="flex items-center gap-4 px-5 py-3.5 transition hover:bg-surface-2"
              >
                <span className="w-6 font-mono text-xs text-muted">{i + 1}</span>
                <span className="flex-1 font-medium">{p.title}</span>
                {solvedSet.has(p.id) && (
                  <span className="rounded-full bg-ok-soft px-2 py-0.5 text-xs font-semibold text-ok">
                    ✓ {t('problems.solved')}
                  </span>
                )}
                <span className="w-20 text-right">
                  <DifficultyTag difficulty={p.difficulty} />
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </Page>
  );
}
