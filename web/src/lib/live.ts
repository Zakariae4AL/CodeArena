import { Client, type StompSubscription } from '@stomp/stompjs';
import { useEffect, useState, useSyncExternalStore } from 'react';

/**
 * One shared STOMP connection. Subscriptions made while disconnected are
 * replayed when the connection comes back.
 */
type Handler = (body: unknown) => void;

const handlers = new Map<string, Set<Handler>>();
const active = new Map<string, StompSubscription>();
const listeners = new Set<() => void>();
let connected = false;
let client: Client | null = null;

function setConnected(value: boolean) {
  connected = value;
  listeners.forEach((l) => l());
}

function subscribeTopic(destination: string) {
  if (!client?.connected || active.has(destination)) return;
  active.set(
    destination,
    client.subscribe(destination, (message) => {
      let body: unknown;
      try {
        body = JSON.parse(message.body);
      } catch {
        return;
      }
      handlers.get(destination)?.forEach((h) => h(body));
    }),
  );
}

function ensureClient() {
  if (client || typeof window === 'undefined' || typeof WebSocket === 'undefined') return;
  const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
  client = new Client({
    brokerURL: `${scheme}://${window.location.host}/ws`,
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      active.clear();
      handlers.forEach((_, destination) => subscribeTopic(destination));
      setConnected(true);
    },
    onWebSocketClose: () => setConnected(false),
  });
  client.activate();
}

export function listen(destination: string, handler: Handler): () => void {
  ensureClient();
  if (!handlers.has(destination)) handlers.set(destination, new Set());
  handlers.get(destination)!.add(handler);
  subscribeTopic(destination);
  return () => {
    const set = handlers.get(destination);
    set?.delete(handler);
    if (set && set.size === 0) {
      handlers.delete(destination);
      active.get(destination)?.unsubscribe();
      active.delete(destination);
    }
  };
}

export function useLiveConnected(): boolean {
  return useSyncExternalStore(
    (cb) => {
      listeners.add(cb);
      return () => listeners.delete(cb);
    },
    () => connected,
    () => false,
  );
}

/** Latest message on a topic (null until the first one). */
export function useTopic<T>(destination: string | null): T | null {
  // Keyed by destination, so switching topics never shows the previous topic's message.
  const [last, setLast] = useState<{ destination: string; body: T } | null>(null);
  useEffect(() => {
    if (!destination) return;
    return listen(destination, (body) => setLast({ destination, body: body as T }));
  }, [destination]);
  return last && last.destination === destination ? last.body : null;
}
