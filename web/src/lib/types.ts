export type Role = 'USER' | 'ADMIN';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type Mode = 'RUN' | 'SUBMIT';
export type Status = 'QUEUED' | 'RUNNING' | 'DONE';
export type Phase = 'UPCOMING' | 'RUNNING' | 'ENDED';
export type Verdict =
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'OUTPUT_LIMIT_EXCEEDED'
  | 'RUNTIME_ERROR'
  | 'COMPILATION_ERROR'
  | 'SYSTEM_ERROR';

export interface User {
  id: number;
  username: string;
  role: Role;
}

export interface TokenResponse {
  token: string;
  expiresInSeconds: number;
  user: User;
}

export interface ProblemSummary {
  id: number;
  slug: string;
  title: string;
  difficulty: Difficulty;
  published: boolean;
}

export interface Sample {
  input: string;
  expected: string;
}

export interface ProblemStatement {
  id: number;
  slug: string;
  title: string;
  difficulty: Difficulty;
  statement: string;
  timeLimitMs: number;
  memoryLimitMb: number;
  samples: Sample[];
}

export interface TestOutcome {
  index: number;
  verdict: Verdict;
  timeMs: number;
  memoryKb: number;
  input?: string;
  output?: string;
  expected?: string;
  stderr?: string;
}

export interface Submission {
  id: string;
  problemId: number;
  problemSlug: string | null;
  problemTitle: string;
  contestId: number | null;
  language: string;
  mode: Mode;
  status: Status;
  verdict: Verdict | null;
  passed: number;
  total: number;
  timeMs: number;
  memoryKb: number;
  compileOutput: string | null;
  message: string | null;
  tests: TestOutcome[];
  source: string | null;
  createdAt: string;
  judgedAt: string | null;
}

/** Pushed over WebSocket while a submission is judged. */
export interface SubmissionUpdate {
  submissionId: string;
  status: Status;
  verdict: Verdict | null;
  passed: number;
  total: number;
  timeMs: number;
  memoryKb: number;
  test: TestOutcome | null;
  tests: TestOutcome[] | null;
  compileOutput: string | null;
  message: string | null;
}

export interface Language {
  id: string;
  label: string;
}

export interface ContestRow {
  id: number;
  slug: string;
  title: string;
  startsAt: string;
  endsAt: string;
  phase: Phase;
  problems: number;
}

export interface ContestProblem {
  label: string;
  problemId: number;
  slug: string;
  title: string;
  difficulty: Difficulty;
}

export interface Contest {
  id: number;
  slug: string;
  title: string;
  description: string;
  startsAt: string;
  endsAt: string;
  phase: Phase;
  serverTime: string;
  problems: ContestProblem[];
}

export interface ScoreCell {
  label: string;
  solved: boolean;
  wrongTries: number;
  minute: number | null;
  firstSolve: boolean;
}

export interface ScoreRow {
  rank: number;
  userId: number;
  username: string;
  solved: number;
  penalty: number;
  cells: ScoreCell[];
}

export interface Scoreboard {
  contestId: number;
  contestSlug: string;
  labels: string[];
  rows: ScoreRow[];
  generatedAt: string;
}

export interface TestInput {
  input: string;
  expected: string;
  sample: boolean;
}

export interface AdminProblem {
  id?: number;
  slug: string;
  title: string;
  statement: string;
  difficulty: Difficulty;
  timeLimitMs: number;
  memoryLimitMb: number;
  published: boolean;
  tests: TestInput[];
}

export interface AdminProblemRow {
  id: number;
  slug: string;
  title: string;
  difficulty: Difficulty;
  published: boolean;
  tests: number;
  createdAt: string;
}

export interface AdminContest {
  id?: number;
  slug: string;
  title: string;
  description: string;
  startsAt: string;
  endsAt: string;
  problemIds: number[];
}

/** RFC 9457 problem details from the API. */
export interface Problem {
  status?: number;
  title?: string;
  detail?: string;
  errors?: Record<string, string>;
}
