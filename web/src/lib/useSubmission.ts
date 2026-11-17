import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useEffect, useMemo, useState } from 'react';
import { api } from './api';
import { listen, useLiveConnected } from './live';
import type { Submission, SubmissionUpdate, TestOutcome } from './types';

export interface LiveSubmission {
  submission: Submission | null;
  /** Per-test results, filled live while judging. */
  tests: TestOutcome[];
  done: boolean;
}

/**
 * Follows a submission: WebSocket updates give per-test progress, and a light poll
 * catches up if the socket is down or the verdict arrived before we subscribed.
 */
export function useSubmission(id: string | null): LiveSubmission {
  const queryClient = useQueryClient();
  const connected = useLiveConnected();
  // Live progress, keyed by submission id so a new submission starts from a clean slate.
  const [progress, setProgress] = useState<{ id: string; tests: Record<number, TestOutcome>; total: number | null }>({
    id: '',
    tests: {},
    total: null,
  });

  const query = useQuery({
    queryKey: ['submissions', id],
    queryFn: () => api<Submission>(`/submissions/${id}`),
    enabled: id !== null,
    refetchInterval: (q) => (q.state.data?.status === 'DONE' ? false : connected ? 4000 : 1500),
  });

  useEffect(() => {
    if (!id) return;
    return listen(`/topic/submissions/${id}`, (body) => {
      const update = body as SubmissionUpdate;
      setProgress((prev) => {
        const base = prev.id === id ? prev : { id, tests: {}, total: null };
        return {
          id,
          total: update.total || base.total,
          tests: update.test ? { ...base.tests, [update.test.index]: update.test } : base.tests,
        };
      });
      if (update.status === 'DONE') {
        void queryClient.invalidateQueries({ queryKey: ['submissions', id] });
        void queryClient.invalidateQueries({ queryKey: ['submissions', 'mine'] });
        void queryClient.invalidateQueries({ queryKey: ['solved'] });
      } else if (update.status === 'RUNNING') {
        queryClient.setQueryData<Submission>(['submissions', id], (old) =>
          old && old.status === 'QUEUED' ? { ...old, status: 'RUNNING', total: update.total } : old,
        );
      }
    });
  }, [id, queryClient]);

  return useMemo(() => {
    const current = progress.id === id ? progress : { tests: {}, total: null };
    const submission = query.data ?? null;
    if (!submission) return { submission: null, tests: [], done: false };
    if (submission.status === 'DONE') return { submission, tests: submission.tests, done: true };
    return {
      submission: { ...submission, total: current.total ?? submission.total },
      tests: Object.values(current.tests).sort((a, b) => a.index - b.index),
      done: false,
    };
  }, [query.data, progress, id]);
}
