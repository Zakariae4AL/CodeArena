import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { useNavigate, useParams } from 'react-router';
import { Page } from '../../components/Layout';
import { ErrorBox, Loading, PageHeader } from '../../components/ui';
import { api } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import type { AdminProblem, Difficulty } from '../../lib/types';

const EMPTY: AdminProblem = {
  slug: '',
  title: '',
  statement: '',
  difficulty: 'EASY',
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  published: false,
  tests: [{ input: '', expected: '', sample: true }],
};

export function ProblemEditor() {
  const { id = 'new' } = useParams();
  const existing = useQuery({
    queryKey: ['admin', 'problem', id],
    queryFn: () => api<AdminProblem>(`/admin/problems/${id}`),
    enabled: id !== 'new',
  });
  if (id !== 'new' && existing.isPending) return <Loading />;
  if (existing.error)
    return (
      <Page>
        <ErrorBox error={existing.error} />
      </Page>
    );
  // Keyed by id: the form starts from the loaded problem, no syncing effect needed.
  return <ProblemForm key={id} id={id} initial={existing.data ?? EMPTY} />;
}

function ProblemForm({ id, initial }: { id: string; initial: AdminProblem }) {
  const isNew = id === 'new';
  const { t } = useI18n();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [form, setForm] = useState<AdminProblem>(initial);
  const [saved, setSaved] = useState(false);

  const save = useMutation({
    mutationFn: (body: AdminProblem) =>
      isNew
        ? api<AdminProblem>('/admin/problems', { method: 'POST', json: body })
        : api<AdminProblem>(`/admin/problems/${id}`, { method: 'PUT', json: body }),
    onSuccess: (p) => {
      setSaved(true);
      void queryClient.invalidateQueries({ queryKey: ['admin'] });
      void queryClient.invalidateQueries({ queryKey: ['problems'] });
      if (isNew) navigate(`/admin/problems/${p.id}`, { replace: true });
    },
  });

  const set = <K extends keyof AdminProblem>(key: K, value: AdminProblem[K]) => {
    setSaved(false);
    setForm((f) => ({ ...f, [key]: value }));
  };
  const setTest = (i: number, patch: Partial<AdminProblem['tests'][number]>) =>
    set(
      'tests',
      form.tests.map((test, j) => (j === i ? { ...test, ...patch } : test)),
    );

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    save.mutate(form);
  }

  return (
    <Page>
      <PageHeader title={isNew ? t('admin.newProblem') : form.title} />
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
            <span>{t('admin.difficulty')}</span>
            <select
              className="field"
              value={form.difficulty}
              onChange={(e) => set('difficulty', e.target.value as Difficulty)}
            >
              {(['EASY', 'MEDIUM', 'HARD'] as const).map((d) => (
                <option key={d} value={d}>
                  {t(`difficulty.${d}`)}
                </option>
              ))}
            </select>
          </label>
          <div className="grid grid-cols-2 gap-3">
            <label className="space-y-1.5 text-sm font-medium">
              <span>{t('admin.timeLimit')}</span>
              <input
                type="number"
                className="field"
                min={100}
                max={15000}
                value={form.timeLimitMs}
                onChange={(e) => set('timeLimitMs', Number(e.target.value))}
              />
            </label>
            <label className="space-y-1.5 text-sm font-medium">
              <span>{t('admin.memoryLimit')}</span>
              <input
                type="number"
                className="field"
                min={16}
                max={1024}
                value={form.memoryLimitMb}
                onChange={(e) => set('memoryLimitMb', Number(e.target.value))}
              />
            </label>
          </div>
          <label className="space-y-1.5 text-sm font-medium md:col-span-2">
            <span>{t('admin.statement')}</span>
            <textarea
              className="field min-h-56 font-mono text-sm"
              value={form.statement}
              onChange={(e) => set('statement', e.target.value)}
              required
            />
          </label>
          <label className="flex items-center gap-2 text-sm font-medium">
            <input type="checkbox" checked={form.published} onChange={(e) => set('published', e.target.checked)} />
            {t('admin.publish')}
          </label>
        </div>

        <section>
          <div className="mb-3 flex items-center justify-between">
            <h2 className="text-sm font-semibold uppercase tracking-wide text-muted">{t('admin.testCases')}</h2>
            <button
              type="button"
              className="btn btn-ghost py-1.5"
              onClick={() => set('tests', [...form.tests, { input: '', expected: '', sample: false }])}
            >
              + {t('admin.addTest')}
            </button>
          </div>
          <ol className="space-y-3">
            {form.tests.map((test, i) => (
              <li key={i} className="card space-y-3 p-4">
                <div className="flex items-center justify-between gap-3 text-sm">
                  <span className="font-semibold">#{i + 1}</span>
                  <label className="flex items-center gap-2">
                    <input
                      type="checkbox"
                      checked={test.sample}
                      onChange={(e) => setTest(i, { sample: e.target.checked })}
                    />
                    {t('admin.sample')}
                  </label>
                  <button
                    type="button"
                    className="ml-auto text-xs text-bad disabled:opacity-40"
                    disabled={form.tests.length === 1}
                    onClick={() =>
                      set(
                        'tests',
                        form.tests.filter((_, j) => j !== i),
                      )
                    }
                  >
                    {t('admin.remove')}
                  </button>
                </div>
                <div className="grid gap-3 md:grid-cols-2">
                  <textarea
                    aria-label={`Input ${i + 1}`}
                    className="field min-h-24 font-mono text-xs"
                    value={test.input}
                    onChange={(e) => setTest(i, { input: e.target.value })}
                    placeholder="input"
                  />
                  <textarea
                    aria-label={`Expected ${i + 1}`}
                    className="field min-h-24 font-mono text-xs"
                    value={test.expected}
                    onChange={(e) => setTest(i, { expected: e.target.value })}
                    placeholder="expected output"
                  />
                </div>
              </li>
            ))}
          </ol>
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
