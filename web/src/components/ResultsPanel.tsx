import { useI18n } from '../lib/i18n';
import { formatMemory, verdictShort, verdictTone } from '../lib/format';
import type { LiveSubmission } from '../lib/useSubmission';
import type { TestOutcome } from '../lib/types';
import { Pre, VerdictBadge } from './ui';

const chipTone = {
  ok: 'border-ok/40 bg-ok-soft text-ok',
  bad: 'border-bad/40 bg-bad-soft text-bad',
  warn: 'border-warn/40 bg-warn-soft text-warn',
  muted: 'border-line bg-surface-2 text-muted',
} as const;

function TestChip({ index, outcome, active }: { index: number; outcome?: TestOutcome; active: boolean }) {
  const { t } = useI18n();
  const tone = outcome ? chipTone[verdictTone(outcome.verdict)] : chipTone.muted;
  return (
    <li
      data-testid={`test-${index}`}
      title={
        outcome
          ? `${t(`verdict.${outcome.verdict}`)} · ${outcome.timeMs} ms · ${formatMemory(outcome.memoryKb)}`
          : undefined
      }
      className={`flex min-w-16 flex-col items-center rounded-lg border px-2 py-1.5 text-xs font-semibold ${tone} ${
        active ? 'animate-pulse border-brand' : ''
      }`}
    >
      <span className="opacity-70">#{index}</span>
      <span>{outcome ? verdictShort[outcome.verdict] : '…'}</span>
      {outcome && <span className="font-mono text-[10px] font-normal opacity-80">{outcome.timeMs} ms</span>}
    </li>
  );
}

export function ResultsPanel({ live }: { live: LiveSubmission }) {
  const { t } = useI18n();
  const { submission, tests, done } = live;
  if (!submission) {
    return <p className="text-sm text-muted">{t('ws.idle')}</p>;
  }

  const total = Math.max(submission.total, tests.length);
  const byIndex = new Map(tests.map((test) => [test.index, test]));
  const indexes = tests.length && done ? tests.map((x) => x.index) : Array.from({ length: total }, (_, i) => i + 1);
  const judged = tests.length;
  const revealed = tests.filter((x) => x.output !== undefined || x.input !== undefined);

  return (
    <div className="space-y-4" aria-live="polite">
      <div className="flex flex-wrap items-center gap-3">
        <VerdictBadge verdict={done ? submission.verdict : null} status={submission.status} />
        <span className="text-sm text-muted">
          {done
            ? submission.verdict === 'COMPILATION_ERROR'
              ? t('ws.compileError')
              : `${t('ws.passed', { passed: submission.passed, total: submission.total })} · ${submission.timeMs} ms · ${formatMemory(submission.memoryKb)}`
            : submission.status === 'QUEUED'
              ? t('ws.queued')
              : t('ws.running', { done: Math.min(judged + 1, total || 1), total: total || '?' })}
        </span>
      </div>

      {!done && total > 0 && (
        <div className="h-1.5 overflow-hidden rounded-full bg-surface-2">
          <div
            className="h-full rounded-full bg-brand transition-all"
            style={{ width: `${(judged / total) * 100}%` }}
          />
        </div>
      )}

      {indexes.length > 0 && submission.verdict !== 'COMPILATION_ERROR' && (
        <ul className="flex flex-wrap gap-2">
          {indexes.map((i) => (
            <TestChip key={i} index={i} outcome={byIndex.get(i)} active={!done && i === judged + 1} />
          ))}
        </ul>
      )}

      {submission.compileOutput && <Pre label={t('ws.compileError')}>{submission.compileOutput}</Pre>}
      {submission.message && <p className="text-sm text-bad">{submission.message}</p>}

      {revealed.map((test) => (
        <div key={test.index} className="space-y-2 rounded-xl border border-line p-3">
          <div className="flex items-center gap-2 text-sm font-semibold">
            {t('ws.test', { n: test.index })} <VerdictBadge verdict={test.verdict} short />
          </div>
          <div className="grid gap-2 md:grid-cols-3">
            <Pre label={t('ws.input')}>{test.input ?? ''}</Pre>
            <Pre label={t('ws.output')}>{test.output ?? ''}</Pre>
            <Pre label={t('ws.expected')}>{test.expected ?? ''}</Pre>
          </div>
          {test.stderr && <Pre label={t('ws.stderr')}>{test.stderr}</Pre>}
        </div>
      ))}
    </div>
  );
}
