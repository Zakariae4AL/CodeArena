import './mocks';
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import type { Contest, ProblemStatement, Scoreboard, Submission } from '../lib/types';
import { admin, alice, fakeApi, renderAt } from './harness';
import { emit } from './mocks';

const statement: ProblemStatement = {
  id: 7,
  slug: 'a-plus-b',
  title: 'A + B',
  difficulty: 'EASY',
  statement: 'Read two integers and print their **sum**.',
  timeLimitMs: 1000,
  memoryLimitMb: 256,
  samples: [{ input: '1 2\n', expected: '3\n' }],
};

function submission(overrides: Partial<Submission> = {}): Submission {
  return {
    id: 'sub-1',
    problemId: 7,
    problemSlug: 'a-plus-b',
    problemTitle: 'A + B',
    contestId: null,
    language: 'cpp',
    mode: 'SUBMIT',
    status: 'QUEUED',
    verdict: null,
    passed: 0,
    total: 0,
    timeMs: 0,
    memoryKb: 0,
    compileOutput: null,
    message: null,
    tests: [],
    source: 'int main(){}',
    createdAt: new Date().toISOString(),
    judgedAt: null,
    ...overrides,
  };
}

describe('Login', () => {
  it('signs in and goes where the user was heading', async () => {
    const api = fakeApi({
      'POST /api/auth/login': (body: unknown) => {
        const { password } = body as { password: string };
        return password === 'codearena1'
          ? { token: 't', expiresInSeconds: 3600, user: alice }
          : new Response(JSON.stringify({ detail: 'Invalid username or password.' }), { status: 401 });
      },
      'GET /api/submissions/mine': [],
    });
    const router = renderAt('/login?next=/submissions');
    const user = userEvent.setup();

    await user.type(screen.getByLabelText('Username'), 'alice');
    await user.type(screen.getByLabelText('Password'), 'wrong');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid username or password.');

    await user.clear(screen.getByLabelText('Password'));
    await user.type(screen.getByLabelText('Password'), 'codearena1');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
    await waitFor(() => expect(router.state.location.pathname).toBe('/submissions'));
    expect(api.calls.filter((c) => c.path === '/api/auth/login').at(-1)?.body).toEqual({
      username: 'alice',
      password: 'codearena1',
    });
  });
});

describe('Problems', () => {
  it('lists problems, marks solved ones and filters', async () => {
    fakeApi(
      {
        'GET /api/problems': [
          { id: 7, slug: 'a-plus-b', title: 'A + B', difficulty: 'EASY', published: true },
          { id: 8, slug: 'count-primes', title: 'Count the Primes', difficulty: 'MEDIUM', published: true },
        ],
        'GET /api/submissions/solved': [7],
      },
      alice,
    );
    renderAt('/problems');
    const row = (await screen.findByText('A + B')).closest('a')!;
    await waitFor(() => expect(within(row).getByText(/Solved/)).toBeInTheDocument());
    expect(screen.getByText('Medium')).toBeInTheDocument();

    await userEvent.setup().type(screen.getByLabelText('Search problems'), 'prime');
    expect(screen.queryByText('A + B')).not.toBeInTheDocument();
    expect(screen.getByText('Count the Primes')).toBeInTheDocument();
  });
});

describe('Workspace', () => {
  it('shows the statement and streams test results live', async () => {
    const api = fakeApi(
      {
        'GET /api/problems/a-plus-b': statement,
        'GET /api/languages': [
          { id: 'cpp', label: 'C++20' },
          { id: 'python', label: 'Python 3' },
        ],
        'POST /api/submissions': submission(),
        'GET /api/submissions/sub-1': submission(),
      },
      alice,
    );
    renderAt('/problems/a-plus-b');

    expect(await screen.findByRole('heading', { name: 'A + B' })).toBeInTheDocument();
    expect(screen.getByText('sum')).toBeInTheDocument();
    expect(screen.getByText('Example 1')).toBeInTheDocument();
    // The C++ template is loaded in the editor.
    expect((screen.getByLabelText('code') as HTMLTextAreaElement).value).toContain('#include');

    const user = userEvent.setup();
    await user.click(await screen.findByRole('button', { name: 'Submit' }));
    await waitFor(() => expect(api.calls.some((c) => c.method === 'POST' && c.path === '/api/submissions')).toBe(true));
    const sent = api.calls.find((c) => c.method === 'POST')!;
    expect(sent.body).toMatchObject({ problemId: 7, language: 'cpp', mode: 'SUBMIT', contestSlug: null });
    expect(sent.auth).toBe('Bearer test-token');
    expect(await screen.findByText('Waiting for a judge…')).toBeInTheDocument();

    const topic = '/topic/submissions/sub-1';
    act(() => {
      emit(topic, { submissionId: 'sub-1', status: 'RUNNING', total: 3, passed: 0, test: null });
      emit(topic, {
        submissionId: 'sub-1',
        status: 'RUNNING',
        total: 3,
        test: { index: 1, verdict: 'ACCEPTED', timeMs: 4, memoryKb: 3000 },
      });
    });
    expect(await screen.findByText('Judging test 2 of 3…')).toBeInTheDocument();
    expect(screen.getByTestId('test-1')).toHaveTextContent('AC');
    // The submit button is locked while judging.
    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled();
  });

  it('asks visitors to sign in instead of submitting', async () => {
    fakeApi({ 'GET /api/problems/a-plus-b': statement, 'GET /api/languages': [] });
    renderAt('/problems/a-plus-b');
    expect(await screen.findByRole('link', { name: 'Sign in to run your code' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument();
  });

  it('shows sample outputs after a run', async () => {
    const done = submission({
      mode: 'RUN',
      status: 'DONE',
      verdict: 'WRONG_ANSWER',
      passed: 0,
      total: 1,
      tests: [
        {
          index: 1,
          verdict: 'WRONG_ANSWER',
          timeMs: 3,
          memoryKb: 2000,
          input: '1 2\n',
          output: '4\n',
          expected: '3\n',
        },
      ],
    });
    fakeApi(
      {
        'GET /api/problems/a-plus-b': statement,
        'GET /api/languages': [],
        'POST /api/submissions': done,
        'GET /api/submissions/sub-1': done,
      },
      alice,
    );
    renderAt('/problems/a-plus-b');
    await userEvent.setup().click(await screen.findByRole('button', { name: /Run examples/ }));
    expect(await screen.findByText('Wrong answer')).toBeInTheDocument();
    expect(screen.getByText('0 / 1 tests passed', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();
  });
});

describe('Contest page', () => {
  const base: Contest = {
    id: 3,
    slug: 'weekly-1',
    title: 'CodeArena Weekly #1',
    description: 'Four problems.',
    startsAt: new Date(Date.now() - 600_000).toISOString(),
    endsAt: new Date(Date.now() + 3_600_000).toISOString(),
    phase: 'RUNNING',
    serverTime: new Date().toISOString(),
    problems: [{ label: 'A', problemId: 7, slug: 'a-plus-b', title: 'A + B', difficulty: 'EASY' }],
  };
  const board: Scoreboard = {
    contestId: 3,
    contestSlug: 'weekly-1',
    labels: ['A'],
    generatedAt: new Date().toISOString(),
    rows: [
      {
        rank: 1,
        userId: 2,
        username: 'alice',
        solved: 1,
        penalty: 25,
        cells: [{ label: 'A', solved: true, wrongTries: 1, minute: 5, firstSolve: true }],
      },
    ],
  };

  it('shows the problems and a scoreboard that updates live', async () => {
    fakeApi({ 'GET /api/contests/weekly-1': base, 'GET /api/contests/weekly-1/scoreboard': board });
    renderAt('/contests/weekly-1');
    expect(await screen.findByRole('heading', { name: 'CodeArena Weekly #1' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /A \+ B/ })).toHaveAttribute('href', '/contests/weekly-1/problems/A');
    expect(await screen.findByText('alice')).toBeInTheDocument();
    expect(screen.getByText('Live')).toBeInTheDocument();

    act(() =>
      emit('/topic/contests/3/scoreboard', {
        ...board,
        rows: [
          {
            rank: 1,
            userId: 9,
            username: 'bob',
            solved: 1,
            penalty: 3,
            cells: [{ label: 'A', solved: true, wrongTries: 0, minute: 3, firstSolve: true }],
          },
          { ...board.rows[0], rank: 2 },
        ],
      }),
    );
    expect(await screen.findByText('bob')).toBeInTheDocument();
    const rows = screen.getAllByRole('row');
    expect(within(rows[1]).getByText('bob')).toBeInTheDocument();
  });

  it('keeps the problems secret before the start', async () => {
    fakeApi({
      'GET /api/contests/weekly-1': {
        ...base,
        phase: 'UPCOMING',
        startsAt: new Date(Date.now() + 86_400_000).toISOString(),
        problems: [],
      },
    });
    renderAt('/contests/weekly-1');
    expect((await screen.findAllByText('The problems will appear when the contest starts.')).length).toBeGreaterThan(0);
    expect(screen.getByText('Starts in')).toBeInTheDocument();
  });
});

describe('Admin', () => {
  it('keeps regular users out', async () => {
    fakeApi({}, alice);
    const router = renderAt('/admin');
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
  });

  it('creates a problem with its test cases', async () => {
    const api = fakeApi(
      {
        'POST /api/admin/problems': (body: unknown) => ({ id: 42, ...(body as object) }),
        'GET /api/admin/problems/42': {
          id: 42,
          slug: 'echo',
          title: 'Echo',
          statement: 'Print it.',
          difficulty: 'EASY',
          timeLimitMs: 1000,
          memoryLimitMb: 256,
          published: true,
          tests: [],
        },
      },
      admin,
    );
    renderAt('/admin/problems/new');
    const user = userEvent.setup();
    await user.type(await screen.findByLabelText('Title'), 'Echo');
    await user.type(screen.getByLabelText('Slug'), 'echo');
    await user.type(screen.getByLabelText('Statement (Markdown)'), 'Print it.');
    await user.click(screen.getByLabelText('Published in the library'));
    await user.type(screen.getByLabelText('Input 1'), 'hi');
    await user.type(screen.getByLabelText('Expected 1'), 'hi');
    await user.click(screen.getByRole('button', { name: '+ Add a test' }));
    await user.type(screen.getByLabelText('Input 2'), 'x');
    await user.type(screen.getByLabelText('Expected 2'), 'x');
    await user.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => expect(api.calls.some((c) => c.method === 'POST')).toBe(true));
    expect(api.calls.find((c) => c.method === 'POST')!.body).toMatchObject({
      slug: 'echo',
      title: 'Echo',
      published: true,
      tests: [
        { input: 'hi', expected: 'hi', sample: true },
        { input: 'x', expected: 'x', sample: false },
      ],
    });
  });
});
