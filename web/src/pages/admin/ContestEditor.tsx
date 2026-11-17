import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router';
import { Page } from '../../components/Layout';
import { ErrorBox, Loading, PageHeader } from '../../components/ui';
import { api } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import type { AdminContest, AdminProblemRow } from '../../lib/types';

/** ISO instant <-> value of an <input type="datetime-local"> in the browser's time zone. */
export function toLocalInput(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function fromLocalInput(value: string): string {
  return new Date(value).toISOString();
}

function defaultContest(): AdminContest {
  const start = new Date(Date.now() + 24 * 3600_000);
  start.setMinutes(0, 0, 0);
  return {
    slug: '',
    title: '',
    description: '',
    startsAt: start.toISOString(),
    endsAt: new Date(start.getTime() + 2 * 3600_000).toISOString(),
    problemIds: [],
  };
}

export function ContestEditor() {
  const { id = 'new' } = useParams();
  const existing = useQuery({
    queryKey: ['admin', 'contest', id],
    queryFn: () => api<AdminContest>(`/admin/contests/${id}`),
    enabled: id !== 'new',
  });
  if (id !== 'new' && existing.isPending) return <Loading />;
  if (existing.error)
    return (
      <Page>
        <ErrorBox error={existing.error} />
      </Page>
    );
  // Keyed by id: the form starts from the loaded contest, no syncing effect needed.
  return <ContestForm key={id} id={id} initial={existing.data ?? defaultContest()} />;
}

function ContestForm({ id, initial }: { id: string; initial: AdminContest }) {
  const isNew = id === 'new';
  const { t } = useI18n();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<AdminContest>(initial);
  const [saved, setSaved] = useState(false);

  const problems = useQuery({
    queryKey: ['admin', 'problems'],
    queryFn: () => api<AdminProblemRow[]>('/admin/problems'),
  });

  const save = useMutation({
    mutationFn: (body: AdminContest) =>
      isNew
        ? api<AdminContest>('/admin/contests', { method: 'POST', json: body })
        : api<AdminContest>(`/admin/contests/${id}`, { method: 'PUT', json: body }),
    onSuccess: (c) => {
      setSaved(true);
      void queryClient.invalidateQueries({ queryKey: ['admin'] });
      void queryClient.invalidateQueries({ queryKey: ['contests'] });
      if (isNew) navigate(`/admin/contests/${c.id}`, { replace: true });
    },
  });

  const set = <K extends keyof AdminContest>(key: K, value: AdminContest[K]) => {
    setSaved(false);
    setForm((f) => ({ ...f, [key]: value }));
  };
  const toggleProblem = (pid: number) =>
    set(
      'problemIds',
      form.problemIds.includes(pid) ? form.problemIds.filter((x) => x !== pid) : [...form.problemIds, pid],
    );

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    save.mutate(form);
  }

  return (
    <Page>
      <PageHeader title={isNew ? t('admin.newContest') : form.title} />
      <form onSubmit={onSubmit} className="space-y-6">
        <div className="card grid gap-4 p-5 md:grid-cols-2">
          <label className="space-y-1.5 text-sm font-medium">
            <span>{t('admin.titleField')}</span>
            <input
              className="field"
              value={form.title}
              onChange={(e) => set('title', e.target.value)}
              required
              maxLength={120}
            />
          </label>
          <label className="space-y-1.5 text-sm font-medium">
            <span>{t('admin.slug')}</span>
            <input
              className="field font-mono"
              value={form.slug}
              onChange={(e) => set('slug', e.target.value)}
              required
              pattern="[a-z0-9]+(-[a-z0-9]+)*"
            />
          </label>
          <label className="space-y-1.5 text-sm font-medium">
            <span>{t('admin.startsAt')}</span>
            <input
              type="datetime-local"
              className="field"
              value={toLocalInput(form.startsAt)}
              onChange={(e) => e.target.value && set('startsAt', fromLocalInput(e.target.value))}
              required
            />
          </label>
          <label className="space-y-1.5 text-sm font-medium">
            <span>{t('admin.endsAt')}</span>
            <input
              type="datetime-local"
              className="field"
              value={toLocalInput(form.endsAt)}
              onChange={(e) => e.target.value && set('endsAt', fromLocalInput(e.target.value))}
              required
            />
          </label>
          <label className="space-y-1.5 text-sm font-medium md:col-span-2">
            <span>{t('admin.description')}</span>
            <textarea
              className="field min-h-20"
              value={form.description}
              onChange={(e) => set('description', e.target.value)}
            />
          </label>
        </div>

        <section>
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted">{t('admin.pickProblems')}</h2>
          {problems.isPending ? (
            <Loading />
          ) : (
            <ul className="card divide-y divide-line">
              {problems.data?.map((p) => {
                const position = form.problemIds.indexOf(p.id);
                return (
                  <li key={p.id}>
                    <label className="flex cursor-pointer items-center gap-3 px-4 py-2.5 text-sm hover:bg-surface-2">
                      <input type="checkbox" checked={position >= 0} onChange={() => toggleProblem(p.id)} />
                      <span className="w-6 font-mono font-bold text-brand">
                        {position >= 0 ? String.fromCharCode(65 + position) : ''}
                      </span>
                      <span className="flex-1">{p.title}</span>
                      {!p.published && <span className="text-xs text-muted">{t('admin.draft')}</span>}
                    </label>
                  </li>
                );
              })}
            </ul>
          )}
        </section>

        {save.error && <ErrorBox error={save.error} />}
        <div className="flex items-center gap-3">
          <button type="submit" className="btn btn-primary" disabled={save.isPending}>
            {t('admin.save')}
          </button>
          {saved && <span className="text-sm text-ok">{t('admin.saved')}</span>}
        </div>
      </form>
    </Page>
  );
}
