import { describe, expect, it, vi } from 'vitest';
import { api, ApiError, setUnauthorizedHandler, tokenStore } from '../lib/api';
import { formatCountdown, formatMemory, formatMinutes, verdictTone } from '../lib/format';
import { translations } from '../lib/i18n';
import { safeNext } from '../pages/Auth';
import { fromLocalInput, toLocalInput } from '../pages/admin/ContestEditor';

describe('format', () => {
  it('maps verdicts to colours', () => {
    expect(verdictTone('ACCEPTED')).toBe('ok');
    expect(verdictTone('WRONG_ANSWER')).toBe('bad');
    expect(verdictTone('TIME_LIMIT_EXCEEDED')).toBe('warn');
    expect(verdictTone(null)).toBe('muted');
  });

  it('formats countdowns, penalties and memory', () => {
    expect(formatCountdown(3_723_000)).toBe('01:02:03');
    expect(formatCountdown(2 * 86_400_000 + 5000)).toBe('2d 00:00:05');
    expect(formatCountdown(-5)).toBe('00:00:00');
    expect(formatMinutes(125)).toBe('2:05');
    expect(formatMemory(512)).toBe('512 KB');
    expect(formatMemory(3277)).toBe('3.2 MB');
  });
});

describe('i18n', () => {
  it('has a French text for every English key', () => {
    const missing = Object.keys(translations.en).filter((k) => !translations.fr[k as keyof typeof translations.fr]);
    expect(missing).toEqual([]);
  });
});

describe('api client', () => {
  it('sends the token and turns problems into readable errors', async () => {
    tokenStore.set('abc');
    const fetchMock = vi.fn(
      async () =>
        new Response(
          JSON.stringify({ status: 400, detail: 'Some fields are invalid.', errors: { title: 'must not be blank' } }),
          {
            status: 400,
          },
        ),
    );
    vi.stubGlobal('fetch', fetchMock);
    await expect(api('/admin/problems', { method: 'POST', json: {} })).rejects.toThrow('title: must not be blank');
    const [, init] = fetchMock.mock.calls[0] as unknown as [string, RequestInit];
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer abc');
  });

  it('signs out when the server rejects the token', async () => {
    tokenStore.set('expired');
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('{}', { status: 401 })),
    );
    await expect(api('/submissions/mine')).rejects.toBeInstanceOf(ApiError);
    expect(handler).toHaveBeenCalled();
  });

  it('reports a network failure', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => Promise.reject(new TypeError('offline'))),
    );
    await expect(api('/problems')).rejects.toMatchObject({ status: 0 });
  });
});

describe('helpers', () => {
  it('only redirects inside the app after login', () => {
    expect(safeNext('/contests/weekly-1')).toBe('/contests/weekly-1');
    expect(safeNext('https://evil.example')).toBe('/problems');
    expect(safeNext('//evil.example')).toBe('/problems');
    expect(safeNext(null)).toBe('/problems');
  });

  it('round-trips datetime-local values', () => {
    const iso = new Date(2026, 11, 1, 18, 30).toISOString();
    expect(fromLocalInput(toLocalInput(iso))).toBe(iso);
  });
});
