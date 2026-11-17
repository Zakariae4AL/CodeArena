import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router';
import { Countdown } from '../components/Countdown';
import { api } from '../lib/api';
import { useI18n } from '../lib/i18n';
import type { ContestRow } from '../lib/types';

const sample = `#include <bits/stdc++.h>
int main() {
    long long a, b;
    std::cin >> a >> b;
    std::cout << a + b;
}`;

export function Home() {
  const { t } = useI18n();
  const contests = useQuery({ queryKey: ['contests'], queryFn: () => api<ContestRow[]>('/contests') });
  const running = contests.data?.find((c) => c.phase === 'RUNNING');

  return (
    <div className="mx-auto max-w-6xl px-4">
      <section className="grid items-center gap-10 py-16 lg:grid-cols-2">
        <div>
          <span className="rounded-full border border-line px-3 py-1 text-xs font-medium text-muted">
            {t('home.badge')}
          </span>
          <h1 className="mt-5 text-4xl font-extrabold leading-tight tracking-tight sm:text-5xl">{t('home.title')}</h1>
          <p className="mt-4 max-w-xl text-muted">{t('home.subtitle')}</p>
          <div className="mt-8 flex flex-wrap gap-3">
            <Link to="/problems" className="btn btn-primary px-5 py-2.5">
              {t('home.cta')} →
            </Link>
            <Link to="/contests" className="btn btn-ghost px-5 py-2.5">
              {t('home.contests')}
            </Link>
          </div>
          {running && (
            <Link
              to={`/contests/${running.slug}`}
              className="card mt-8 flex items-center gap-4 p-4 transition hover:border-brand"
            >
              <span className="inline-flex items-center gap-1.5 rounded-full bg-bad-soft px-2 py-0.5 text-[10px] font-bold uppercase text-bad">
                <span className="h-1.5 w-1.5 animate-pulse rounded-full bg-bad" /> {t('home.live')}
              </span>
              <span className="flex-1 font-medium">{running.title}</span>
              <span className="text-sm text-muted">
                <Countdown target={running.endsAt} />
              </span>
            </Link>
          )}
        </div>

        <div className="card overflow-hidden shadow-2xl shadow-brand/5" aria-hidden="true">
          <div className="flex items-center gap-1.5 border-b border-line px-4 py-2.5">
            <span className="h-2.5 w-2.5 rounded-full bg-bad/70" />
            <span className="h-2.5 w-2.5 rounded-full bg-warn/70" />
            <span className="h-2.5 w-2.5 rounded-full bg-ok/70" />
            <span className="ml-3 font-mono text-xs text-muted">main.cpp</span>
          </div>
          <pre className="px-5 py-4 font-mono text-sm leading-relaxed">{sample}</pre>
          <div className="space-y-2 border-t border-line px-5 py-4">
            <div className="flex items-center gap-2 text-sm">
              <span className="rounded-full bg-ok-soft px-2.5 py-0.5 text-xs font-semibold text-ok">Accepted</span>
              <span className="text-muted">8 / 8 · 4 ms · 3.2 MB</span>
            </div>
            <div className="flex gap-1.5">
              {Array.from({ length: 8 }, (_, i) => (
                <span key={i} className="h-2 flex-1 rounded-full bg-ok" style={{ opacity: 0.45 + i * 0.07 }} />
              ))}
            </div>
          </div>
        </div>
      </section>

      <section className="pb-16">
        <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-muted">{t('home.how')}</h2>
        <ol className="grid gap-4 md:grid-cols-3">
          {(['home.step1', 'home.step2', 'home.step3'] as const).map((key, i) => (
            <li key={key} className="card p-5">
              <span className="font-mono text-2xl font-bold text-brand">0{i + 1}</span>
              <p className="mt-2 text-sm">{t(key)}</p>
            </li>
          ))}
        </ol>
      </section>
    </div>
  );
}
