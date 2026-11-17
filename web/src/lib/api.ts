import type { Problem } from './types';

const TOKEN_KEY = 'codearena.token';

export const tokenStore = {
  get(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  },
  set(token: string | null) {
    try {
      if (token) localStorage.setItem(TOKEN_KEY, token);
      else localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* storage blocked: the session lasts until the tab closes */
    }
  },
};

export class ApiError extends Error {
  readonly status: number;
  readonly problem: Problem;

  constructor(status: number, problem: Problem) {
    const fields = problem.errors
      ? Object.entries(problem.errors)
          .map(([field, message]) => `${field}: ${message}`)
          .join(' · ')
      : '';
    super(fields || problem.detail || `Request failed (${status})`);
    this.status = status;
    this.problem = problem;
  }
}

let onUnauthorized: () => void = () => {};
/** The auth provider registers what to do when a token is rejected. */
export function setUnauthorizedHandler(handler: () => void) {
  onUnauthorized = handler;
}

export async function api<T>(path: string, init: RequestInit & { json?: unknown } = {}): Promise<T> {
  const headers = new Headers(init.headers);
  const token = tokenStore.get();
  if (token) headers.set('Authorization', `Bearer ${token}`);
  let body = init.body;
  if (init.json !== undefined) {
    headers.set('Content-Type', 'application/json');
    body = JSON.stringify(init.json);
  }
  let response: Response;
  try {
    response = await fetch(`/api${path}`, { ...init, headers, body });
  } catch {
    throw new ApiError(0, { detail: 'Cannot reach the server.' });
  }
  if (response.status === 401 && token && !path.startsWith('/auth/')) {
    onUnauthorized();
  }
  if (!response.ok) {
    let problem: Problem = { status: response.status };
    try {
      problem = await response.json();
    } catch {
      /* not JSON */
    }
    throw new ApiError(response.status, problem);
  }
  if (response.status === 204) return undefined as T;
  return response.json() as Promise<T>;
}
