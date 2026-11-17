import { useEffect, useState } from 'react';
import { formatCountdown } from '../lib/format';

/** Counts down to `target`, corrected by the server's clock when given. */
export function Countdown({
  target,
  serverTime,
  onDone,
}: {
  target: string;
  serverTime?: string;
  onDone?: () => void;
}) {
  const [offset] = useState(() => (serverTime ? new Date(serverTime).getTime() - Date.now() : 0));
  const [now, setNow] = useState(() => Date.now() + offset);
  const remaining = new Date(target).getTime() - now;

  useEffect(() => {
    const id = setInterval(() => setNow(Date.now() + offset), 1000);
    return () => clearInterval(id);
  }, [offset]);

  useEffect(() => {
    if (remaining <= 0) onDone?.();
  }, [remaining <= 0]); // eslint-disable-line react-hooks/exhaustive-deps

  return <span className="font-mono tabular-nums">{formatCountdown(remaining)}</span>;
}
