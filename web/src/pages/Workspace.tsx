import { useMutation, useQuery } from '@tanstack/react-query';
import { useEffect, useRef, useState } from 'react';
import { Link, useParams } from 'react-router';
import { CodeEditor } from '../components/CodeEditor';
import { Countdown } from '../components/Countdown';
import { Markdown } from '../components/Markdown';
import { ResultsPanel } from '../components/ResultsPanel';
import { DifficultyTag, ErrorBox, Loading, Pre, VerdictBadge } from '../components/ui';
import { api } from '../lib/api';
import { useAuth } from '../lib/auth';
import { formatDate } from '../lib/format';
import { useI18n } from '../lib/i18n';
import { drafts, templates } from '../lib/templates';
import type { Contest, Language, Mode, ProblemStatement, Submission } from '../lib/types';
import { useSubmission } from '../lib/useSubmission';

const FALLBACK_LANGUAGES: Language[] = [
  { id: 'cpp', label: 'C++20' },
  { id: 'java', label: 'Java 25' },
  { id: 'python', label: 'Python 3' },
  { id: 'javascript', label: 'JavaScript (Node.js)' },
];

function useStatement(slug?: string, contestSlug?: string, label?: string) {
  return useQuery({
    queryKey: ['statement', slug, contestSlug, label],
    queryFn: async (): Promise<{ problem: ProblemStatement; label?: string }> => {
      if (contestSlug && label) {
        const res = await api<{ label: string; problem: ProblemStatement }>(
          `/contests/${contestSlug}/problems/${label}`,
        );
        return { problem: res.problem, label: res.label };
      }
      return { problem: await api<ProblemStatement>(`/problems/${slug}`) };
    },
  });
}

/** Solve a problem from the library (/problems/:slug) or inside a contest (/contests/:contestSlug/problems/:label). */
export function Workspace() {
  const { slug, contestSlug, label } = useParams();
  const { user } = useAuth();
  const { t, locale } = useI18n();
  const statement = useStatement(slug, contestSlug, label);
  const contest = useQuery({
    queryKey: ['contest', contestSlug],
    queryFn: () => api<Contest>(`/contests/${contestSlug}`),
    enabled: Boolean(contestSlug),
  });
  const languages = useQuery({
    queryKey: ['languages'],
    queryFn: () => api<Language[]>('/languages'),
    staleTime: Infinity,
  });

  const problem = statement.data?.problem;
  const [language, setLanguage] = useState(() => {
    try {
      return localStorage.getItem('codearena.language') ?? 'cpp';
    } catch {
      return 'cpp';
    }
  });
  // The code belongs to one (problem, language) pair; switching either loads that pair's draft.
  const [editor, setEditor] = useState<{ key: string; code: string } | null>(null);
  const [tab, setTab] = useState<'statement' | 'submissions'>('statement');
  const [currentId, setCurrentId] = useState<string | null>(null);
  const live = useSubmission(currentId);
  const saveTimer = useRef<ReturnType<typeof setTimeout>>(undefined);

  const editorKey = problem ? `${problem.id}:${language}` : '';
  const code = editor?.key === editorKey ? editor.code : problem ? drafts.load(problem.id, language) : '';

  useEffect(() => {
    try {
      localStorage.setItem('codearena.language', language);
    } catch {
      /* ignore */
    }
  }, [language]);

  const onCodeChange = (value: string) => {
    setEditor({ key: editorKey, code: value });
    clearTimeout(saveTimer.current);
    saveTimer.current = setTimeout(() => problem && drafts.save(problem.id, language, value), 400);
  };

  const submit = useMutation({
    mutationFn: (mode: Mode) =>
      api<Submission>('/submissions', {
        method: 'POST',
        json: { problemId: problem!.id, contestSlug: contestSlug ?? null, language, source: code, mode },
      }),
    onSuccess: (s) => setCurrentId(s.id),
  });

  const history = useQuery({
    queryKey: ['submissions', 'mine', problem?.id],
    queryFn: () => api<Submission[]>(`/submissions/mine?problemId=${problem!.id}&limit=20`),
    enabled: Boolean(user && problem && tab === 'submissions'),
  });

  if (statement.isPending) return <Loading />;
  if (statement.error)
    return (
      <div className="mx-auto max-w-3xl p-8">
        <ErrorBox error={statement.error} />
      </div>
    );
  if (!problem) return null;

  const judging = live.submission !== null && !live.done;
  const title = statement.data?.label ? `${statement.data.label}. ${problem.title}` : problem.title;

  return (
    <div className="mx-auto grid grid-cols-[minmax(0,1fr)] max-w-[1400px] gap-4 px-4 py-4 lg:h-[calc(100vh-3.5rem)] lg:grid-cols-[minmax(0,5fr)_minmax(0,7fr)]">
      {/* Left: statement and history */}
      <section className="card flex min-h-0 flex-col overflow-hidden">
        <div className="flex items-center gap-1 border-b border-line px-3 pt-2">
          {(['statement', 'submissions'] as const).map((name) => (
            <button
              key={name}
              type="button"
              onClick={() => setTab(name)}
              className={`-mb-px border-b-2 px-3 py-2 text-sm font-medium ${
                tab === name ? 'border-brand text-text' : 'border-transparent text-muted hover:text-text'
              }`}
            >
              {t(name === 'statement' ? 'ws.statement' : 'ws.submissions')}
            </button>
          ))}
          {contest.data && (
            <Link to={`/contests/${contest.data.slug}`} className="ml-auto pb-1 text-xs text-muted hover:text-text">
              {contest.data.title}
              {contest.data.phase === 'RUNNING' && (
                <span className="ml-2 rounded bg-brand-soft px-1.5 py-0.5 text-brand">
                  <Countdown target={contest.data.endsAt} serverTime={contest.data.serverTime} />
                </span>
              )}
            </Link>
          )}
        </div>

        <div className="min-h-0 flex-1 overflow-y-auto p-5">
          {tab === 'statement' ? (
            <article>
              <h1 className="text-xl font-bold tracking-tight">{title}</h1>
              <div className="mt-1 flex items-center gap-3 text-xs text-muted">
                <DifficultyTag difficulty={problem.difficulty} />
                <span>{t('ws.limits', { time: problem.timeLimitMs / 1000, memory: problem.memoryLimitMb })}</span>
              </div>
              {contest.data?.phase === 'ENDED' && (
                <p className="mt-3 rounded-lg bg-surface-2 px-3 py-2 text-xs text-muted">{t('ws.contestPractice')}</p>
              )}
              <div className="mt-4">
                <Markdown>{problem.statement}</Markdown>
              </div>
              {problem.samples.map((sample, i) => (
                <div key={i} className="mt-5">
                  <h3 className="mb-2 text-sm font-semibold">{t('ws.example', { n: i + 1 })}</h3>
                  <div className="grid gap-3 sm:grid-cols-2">
                    <Pre label={t('ws.input')}>{sample.input}</Pre>
                    <Pre label={t('ws.output')}>{sample.expected}</Pre>
                  </div>
                </div>
              ))}
            </article>
          ) : !user ? (
            <p className="text-sm text-muted">{t('ws.signin')}</p>
          ) : history.isPending ? (
            <Loading />
          ) : history.data?.length ? (
            <ul className="divide-y divide-line">
              {history.data.map((s) => (
                <li key={s.id} className="flex items-center justify-between gap-3 py-2.5 text-sm">
                  <Link to={`/submissions/${s.id}`} className="flex items-center gap-3 hover:underline">
                    <VerdictBadge verdict={s.verdict} status={s.status} />
                    <span className="text-muted">{s.language}</span>
                  </Link>
                  <span className="text-xs text-muted">{formatDate(s.createdAt, locale)}</span>
                </li>
              ))}
            </ul>
          ) : (
            <p className="text-sm text-muted">{t('ws.none')}</p>
          )}
        </div>
      </section>

      {/* Right: editor and results */}
      <section className="flex min-h-0 flex-col gap-4">
        <div className="card flex min-h-[420px] flex-1 flex-col overflow-hidden">
          <div className="flex items-center gap-2 border-b border-line px-3 py-2">
            <select
              aria-label="Language"
              className="field w-auto py-1.5"
              value={language}
              onChange={(e) => setLanguage(e.target.value)}
            >
              {(languages.data ?? FALLBACK_LANGUAGES).map((l) => (
                <option key={l.id} value={l.id}>
                  {l.label}
                </option>
              ))}
            </select>
            <button
              type="button"
              className="btn btn-ghost py-1.5 text-xs"
              onClick={() => onCodeChange(templates[language] ?? '')}
            >
              {t('ws.reset')}
            </button>
            <div className="ml-auto flex gap-2">
              {user ? (
                <>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    disabled={judging || submit.isPending || !code.trim()}
                    onClick={() => submit.mutate('RUN')}
                  >
                    ▶ {t('ws.run')}
                  </button>
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={judging || submit.isPending || !code.trim() || contest.data?.phase === 'UPCOMING'}
                    onClick={() => submit.mutate('SUBMIT')}
                  >
                    {t('ws.submit')}
                  </button>
                </>
              ) : (
                <Link to="/login" className="btn btn-primary">
                  {t('ws.signin')}
                </Link>
              )}
            </div>
          </div>
          <div className="min-h-0 flex-1">
            <CodeEditor value={code} language={language} onChange={onCodeChange} />
          </div>
        </div>

        <div className="card max-h-[45%] min-h-40 overflow-y-auto p-4">
          <h2 className="mb-3 text-sm font-semibold uppercase tracking-wide text-muted">{t('ws.results')}</h2>
          {submit.error ? <ErrorBox error={submit.error} /> : <ResultsPanel live={live} />}
        </div>
      </section>
    </div>
  );
}
