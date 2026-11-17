import { useQuery } from '@tanstack/react-query';
import { Link, useParams } from 'react-router';
import { CodeEditor } from '../components/CodeEditor';
import { Page } from '../components/Layout';
import { ResultsPanel } from '../components/ResultsPanel';
import { Empty, ErrorBox, Loading, PageHeader, VerdictBadge } from '../components/ui';
import { api } from '../lib/api';
import { formatDate, formatMemory } from '../lib/format';
import { useI18n } from '../lib/i18n';
import type { Submission } from '../lib/types';
import { useSubmission } from '../lib/useSubmission';

export function Submissions() {
  const { t, locale } = useI18n();
  const list = useQuery({ queryKey: ['submissions', 'mine'], queryFn: () => api<Submission[]>('/submissions/mine') });

  return (
    <Page>
      <PageHeader title={t('subs.title')} />
      {list.isPending ? (
        <Loading />
      ) : list.error ? (
        <ErrorBox error={list.error} />
      ) : list.data.length === 0 ? (
        <Empty>{t('subs.empty')}</Empty>
      ) : (
        <div className="card overflow-x-auto">
          <table className="w-full text-sm">
            <thead>
              <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-muted">
                <th className="px-4 py-2.5">{t('subs.problem')}</th>
                <th className="px-4 py-2.5">{t('subs.verdict')}</th>
                <th className="px-4 py-2.5">{t('subs.language')}</th>
                <th className="px-4 py-2.5">{t('subs.time')}</th>
                <th className="px-4 py-2.5">{t('subs.memory')}</th>
                <th className="px-4 py-2.5">{t('subs.when')}</th>
              </tr>
            </thead>
            <tbody>
              {list.data.map((s) => (
                <tr key={s.id} className="border-b border-line last:border-0 hover:bg-surface-2">
                  <td className="px-4 py-2.5 font-medium">
                    <Link to={`/submissions/${s.id}`} className="hover:underline">
                      {s.problemTitle}
                    </Link>
                  </td>
                  <td className="px-4 py-2.5">
                    <VerdictBadge verdict={s.verdict} status={s.status} />
                  </td>
                  <td className="px-4 py-2.5 text-muted">{s.language}</td>
                  <td className="px-4 py-2.5 font-mono text-muted">{s.status === 'DONE' ? `${s.timeMs} ms` : '—'}</td>
                  <td className="px-4 py-2.5 font-mono text-muted">
                    {s.status === 'DONE' ? formatMemory(s.memoryKb) : '—'}
                  </td>
                  <td className="px-4 py-2.5 text-muted">{formatDate(s.createdAt, locale)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </Page>
  );
}

export function SubmissionDetail() {
  const { id } = useParams();
  const { t, locale } = useI18n();
  const live = useSubmission(id ?? null);
  const s = live.submission;

  if (!s) return <Loading />;
  return (
    <Page>
      <PageHeader
        title={`${t('subs.detail')} · ${s.problemTitle}`}
        subtitle={`${s.language} · ${formatDate(s.createdAt, locale)}`}
        actions={
          s.problemSlug && (
            <Link to={`/problems/${s.problemSlug}`} className="btn btn-ghost">
              {t('common.back')}
            </Link>
          )
        }
      />
      <div className="grid grid-cols-[minmax(0,1fr)] gap-6 lg:grid-cols-2">
        <section className="card p-5">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted">{t('subs.tests')}</h2>
          <ResultsPanel live={live} />
        </section>
        <section className="card h-[520px] overflow-hidden">
          <h2 className="border-b border-line px-4 py-2.5 text-sm font-semibold uppercase tracking-wide text-muted">
            {t('subs.code')}
          </h2>
          <div className="h-[calc(100%-2.75rem)]">
            {s.source ? <CodeEditor value={s.source} language={s.language} readOnly /> : <ErrorBox error={null} />}
          </div>
        </section>
      </div>
    </Page>
  );
}
