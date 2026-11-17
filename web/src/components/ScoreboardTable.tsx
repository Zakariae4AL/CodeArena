import { useI18n } from '../lib/i18n';
import { formatMinutes } from '../lib/format';
import type { Scoreboard } from '../lib/types';

export function ScoreboardTable({ board, me }: { board: Scoreboard; me?: number }) {
  const { t } = useI18n();
  if (board.rows.length === 0) {
    return <p className="px-4 py-8 text-center text-sm text-muted">{t('board.empty')}</p>;
  }
  return (
    <div className="overflow-x-auto">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-line text-left text-xs uppercase tracking-wide text-muted">
            <th className="px-3 py-2 w-12">{t('board.rank')}</th>
            <th className="px-3 py-2">{t('board.user')}</th>
            <th className="px-3 py-2 text-center">{t('board.solved')}</th>
            <th className="px-3 py-2 text-center">{t('board.penalty')}</th>
            {board.labels.map((label) => (
              <th key={label} className="px-2 py-2 text-center">
                {label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {board.rows.map((row) => (
            <tr
              key={row.userId}
              className={`border-b border-line last:border-0 ${row.userId === me ? 'bg-brand-soft' : ''}`}
            >
              <td className="px-3 py-2.5 font-mono font-semibold">{row.rank}</td>
              <td className="px-3 py-2.5 font-medium">{row.username}</td>
              <td className="px-3 py-2.5 text-center font-semibold">{row.solved}</td>
              <td className="px-3 py-2.5 text-center font-mono text-muted">{row.penalty}</td>
              {row.cells.map((cell) => (
                <td key={cell.label} className="px-1.5 py-1.5 text-center">
                  {cell.solved ? (
                    <div
                      className={`rounded-md px-1 py-1 font-mono text-xs font-semibold ${
                        cell.firstSolve ? 'bg-ok text-bg' : 'bg-ok-soft text-ok'
                      }`}
                      title={cell.firstSolve ? 'First to solve' : undefined}
                    >
                      +{cell.wrongTries || ''}
                      <div className="text-[10px] font-normal opacity-80">{formatMinutes(cell.minute ?? 0)}</div>
                    </div>
                  ) : cell.wrongTries > 0 ? (
                    <div className="rounded-md bg-bad-soft px-1 py-1 font-mono text-xs font-semibold text-bad">
                      −{cell.wrongTries}
                    </div>
                  ) : (
                    <span className="text-muted">·</span>
                  )}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
