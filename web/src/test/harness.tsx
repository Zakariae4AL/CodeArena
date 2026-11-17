import { QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { vi } from 'vitest';
import { createQueryClient, routes } from '../App';
import { AuthProvider } from '../lib/auth';
import { I18nProvider } from '../lib/i18n';
import type { User } from '../lib/types';

type Handler = (body: unknown, url: URL) => unknown | Response;
export type Routes = Record<string, Handler | unknown>;

export interface FakeApi {
  calls: { method: string; path: string; body: unknown; auth: string | null }[];
}

/** Replaces fetch with a tiny router: keys are "GET /api/problems", values are data or handlers. */
export function fakeApi(table: Routes, user?: User): FakeApi {
  const api: FakeApi = { calls: [] };
  if (user) localStorage.setItem('codearena.token', 'test-token');
  const all: Routes = user ? { 'GET /api/auth/me': user, ...table } : table;
  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: string, init: RequestInit = {}) => {
      const url = new URL(input, 'http://localhost');
      const method = (init.method ?? 'GET').toUpperCase();
      const body = typeof init.body === 'string' ? JSON.parse(init.body) : undefined;
      const auth = new Headers(init.headers).get('Authorization');
      api.calls.push({ method, path: url.pathname + url.search, body, auth });
      const handler = all[`${method} ${url.pathname}${url.search}`] ?? all[`${method} ${url.pathname}`];
      if (handler === undefined) return new Response(JSON.stringify({ detail: 'not found' }), { status: 404 });
      const result = typeof handler === 'function' ? (handler as Handler)(body, url) : handler;
      if (result instanceof Response) return result;
      return new Response(JSON.stringify(result), { status: method === 'POST' ? 201 : 200 });
    }),
  );
  return api;
}

export function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <QueryClientProvider client={createQueryClient()}>
      <I18nProvider lang="en">
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </I18nProvider>
    </QueryClientProvider>,
  );
  return router;
}

export const alice: User = { id: 2, username: 'alice', role: 'USER' };
export const admin: User = { id: 1, username: 'admin', role: 'ADMIN' };
