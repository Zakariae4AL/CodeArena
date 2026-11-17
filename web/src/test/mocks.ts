import { vi } from 'vitest';

/** A controllable stand-in for the STOMP connection. */
export const topics = new Map<string, Set<(body: unknown) => void>>();

export function emit(destination: string, body: unknown) {
  topics.get(destination)?.forEach((handler) => handler(body));
}

vi.mock('../lib/live', async () => {
  const { useEffect, useState } = await import('react');
  const listen = (destination: string, handler: (body: unknown) => void) => {
    if (!topics.has(destination)) topics.set(destination, new Set());
    topics.get(destination)!.add(handler);
    return () => {
      topics.get(destination)?.delete(handler);
    };
  };
  return {
    listen,
    useLiveConnected: () => true,
    useTopic<T>(destination: string | null): T | null {
      const [last, setLast] = useState<T | null>(null);
      useEffect(() => (destination ? listen(destination, (b) => setLast(b as T)) : undefined), [destination]);
      return last;
    },
  };
});

// Monaco cannot run in jsdom: a textarea stands in for the editor.
vi.mock('../components/CodeEditor', async () => {
  const { createElement } = await import('react');
  return {
    CodeEditor: ({
      value,
      onChange,
      readOnly,
    }: {
      value: string;
      onChange?: (v: string) => void;
      readOnly?: boolean;
    }) =>
      createElement('textarea', {
        'aria-label': 'code',
        value,
        readOnly,
        onChange: (e: { target: { value: string } }) => onChange?.(e.target.value),
      }),
  };
});
