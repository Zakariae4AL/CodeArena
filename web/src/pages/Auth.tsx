import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { ErrorBox } from '../components/ui';
import { useAuth } from '../lib/auth';
import { useI18n } from '../lib/i18n';

/** Only follow ?next= to a page of this app (no open redirect). */
export function safeNext(next: string | null): string {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/problems';
}

export function Login() {
  const { t } = useI18n();
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setBusy(true);
    setError(null);
    try {
      await login(String(form.get('username')), String(form.get('password')));
      navigate(safeNext(params.get('next')), { replace: true });
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-sm px-4 py-16">
      <form onSubmit={onSubmit} className="card space-y-4 p-7">
        <h1 className="text-xl font-bold">{t('auth.loginTitle')}</h1>
        <label className="block space-y-1.5 text-sm font-medium">
          <span>{t('auth.username')}</span>
          <input name="username" className="field" autoComplete="username" required />
        </label>
        <label className="block space-y-1.5 text-sm font-medium">
          <span>{t('auth.password')}</span>
          <input name="password" type="password" className="field" autoComplete="current-password" required />
        </label>
        {error !== null && <ErrorBox error={error} />}
        <button type="submit" className="btn btn-primary w-full" disabled={busy}>
          {t('auth.login')}
        </button>
        <p className="text-center text-sm text-muted">
          {t('auth.noAccount')}{' '}
          <Link to="/register" className="font-medium text-brand">
            {t('auth.register')}
          </Link>
        </p>
        <p className="rounded-lg bg-surface-2 px-3 py-2 text-xs text-muted">
          {t('auth.demo', { admin: 'admin', user: 'alice', password: 'codearena1' })}
        </p>
      </form>
    </div>
  );
}

export function Register() {
  const { t } = useI18n();
  const { register } = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState<unknown>(null);
  const [busy, setBusy] = useState(false);

  async function onSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault();
    const form = new FormData(e.currentTarget);
    setBusy(true);
    setError(null);
    try {
      await register(String(form.get('username')), String(form.get('email')), String(form.get('password')));
      navigate('/problems', { replace: true });
    } catch (err) {
      setError(err);
      setBusy(false);
    }
  }

  return (
    <div className="mx-auto max-w-sm px-4 py-16">
      <form onSubmit={onSubmit} className="card space-y-4 p-7">
        <h1 className="text-xl font-bold">{t('auth.registerTitle')}</h1>
        <label className="block space-y-1.5 text-sm font-medium">
          <span>{t('auth.username')}</span>
          <input name="username" className="field" autoComplete="username" required pattern="[A-Za-z0-9_]{3,20}" />
        </label>
        <label className="block space-y-1.5 text-sm font-medium">
          <span>{t('auth.email')}</span>
          <input name="email" type="email" className="field" autoComplete="email" required />
        </label>
        <label className="block space-y-1.5 text-sm font-medium">
          <span>{t('auth.password')}</span>
          <input name="password" type="password" className="field" autoComplete="new-password" required minLength={8} />
          <span className="block text-xs font-normal text-muted">{t('auth.passwordHint')}</span>
        </label>
        {error !== null && <ErrorBox error={error} />}
        <button type="submit" className="btn btn-primary w-full" disabled={busy}>
          {t('auth.register')}
        </button>
        <p className="text-center text-sm text-muted">
          {t('auth.hasAccount')}{' '}
          <Link to="/login" className="font-medium text-brand">
            {t('auth.login')}
          </Link>
        </p>
      </form>
    </div>
  );
}

export function NotFound() {
  const { t } = useI18n();
  return (
    <div className="mx-auto max-w-md px-4 py-24 text-center">
      <p className="font-mono text-5xl font-bold text-brand">404</p>
      <h1 className="mt-3 text-xl font-semibold">{t('notFound.title')}</h1>
      <Link to="/problems" className="btn btn-primary mt-6">
        {t('notFound.back')}
      </Link>
    </div>
  );
}
