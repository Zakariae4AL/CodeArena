import { NavLink, Outlet, useNavigate } from 'react-router';
import { useAuth } from '../lib/auth';
import { useI18n } from '../lib/i18n';
import { useTheme } from '../lib/theme';

const linkClass = ({ isActive }: { isActive: boolean }) =>
  `rounded-lg px-3 py-1.5 text-sm font-medium transition ${
    isActive ? 'bg-surface-2 text-text' : 'text-muted hover:text-text'
  }`;

export function Layout() {
  const { user, logout } = useAuth();
  const { t, toggle } = useI18n();
  const [theme, toggleTheme] = useTheme();
  const navigate = useNavigate();

  return (
    <div className="flex min-h-full flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:left-2 focus:top-2 focus:z-50 focus:rounded focus:bg-surface focus:px-3 focus:py-2"
      >
        Skip to content
      </a>
      <header className="sticky top-0 z-20 border-b border-line bg-bg/85 backdrop-blur">
        <div className="mx-auto flex max-w-[1400px] flex-wrap items-center gap-x-4 gap-y-1 px-4 py-2.5 sm:h-14 sm:flex-nowrap sm:py-0">
          <NavLink to="/" className="flex items-center gap-2 font-bold tracking-tight">
            <span className="grid h-7 w-7 place-items-center rounded-lg bg-brand font-mono text-sm text-brand-ink">
              {'</>'}
            </span>
            CodeArena
          </NavLink>
          <nav
            className="order-last -mx-1 flex w-full items-center gap-1 overflow-x-auto pb-1 sm:order-none sm:mx-0 sm:w-auto sm:flex-1 sm:pb-0"
            aria-label="Main"
          >
            <NavLink to="/problems" className={linkClass}>
              {t('nav.problems')}
            </NavLink>
            <NavLink to="/contests" className={linkClass}>
              {t('nav.contests')}
            </NavLink>
            {user && (
              <NavLink to="/submissions" className={linkClass}>
                {t('nav.submissions')}
              </NavLink>
            )}
            {user?.role === 'ADMIN' && (
              <NavLink to="/admin" className={linkClass}>
                {t('nav.admin')}
              </NavLink>
            )}
          </nav>
          <div className="ml-auto flex items-center gap-2">
            <button type="button" className="btn btn-ghost px-2.5 py-1.5 text-xs" onClick={toggle}>
              {t('nav.lang')}
            </button>
            <button
              type="button"
              className="btn btn-ghost px-2.5 py-1.5"
              onClick={toggleTheme}
              aria-label={t('nav.theme')}
            >
              {theme === 'dark' ? '☀' : '☾'}
            </button>
            {user ? (
              <>
                <span className="hidden text-sm text-muted sm:inline">{user.username}</span>
                <button
                  type="button"
                  className="btn btn-ghost py-1.5"
                  onClick={() => {
                    logout();
                    navigate('/');
                  }}
                >
                  {t('nav.logout')}
                </button>
              </>
            ) : (
              <NavLink to="/login" className="btn btn-primary py-1.5">
                {t('nav.login')}
              </NavLink>
            )}
          </div>
        </div>
      </header>
      <main id="main" className="flex-1">
        <Outlet />
      </main>
    </div>
  );
}

export function Page({ children }: { children: React.ReactNode }) {
  return <div className="mx-auto max-w-6xl px-4 py-8">{children}</div>;
}
