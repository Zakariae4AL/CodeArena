import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { createBrowserRouter, RouterProvider, type RouteObject } from 'react-router';
import { Layout } from './components/Layout';
import { RequireAuth } from './components/RequireAuth';
import { AuthProvider } from './lib/auth';
import { I18nProvider } from './lib/i18n';
import { ApiError } from './lib/api';
import { Login, NotFound, Register } from './pages/Auth';
import { ContestPage } from './pages/ContestPage';
import { Contests } from './pages/Contests';
import { Home } from './pages/Home';
import { Problems } from './pages/Problems';
import { lazy, Suspense, type ReactNode } from 'react';
import { Loading } from './components/ui';

// Heavier screens (Markdown, editor, admin forms) are loaded on demand.
const Workspace = lazy(() => import('./pages/Workspace').then((m) => ({ default: m.Workspace })));
const Submissions = lazy(() => import('./pages/Submissions').then((m) => ({ default: m.Submissions })));
const SubmissionDetail = lazy(() => import('./pages/Submissions').then((m) => ({ default: m.SubmissionDetail })));
const AdminHome = lazy(() => import('./pages/admin/AdminHome').then((m) => ({ default: m.AdminHome })));
const ProblemEditor = lazy(() => import('./pages/admin/ProblemEditor').then((m) => ({ default: m.ProblemEditor })));
const ContestEditor = lazy(() => import('./pages/admin/ContestEditor').then((m) => ({ default: m.ContestEditor })));

const lazyPage = (node: ReactNode) => <Suspense fallback={<Loading />}>{node}</Suspense>;

export const routes: RouteObject[] = [
  {
    element: <Layout />,
    children: [
      { path: '/', element: <Home /> },
      { path: '/problems', element: <Problems /> },
      { path: '/problems/:slug', element: lazyPage(<Workspace />) },
      { path: '/contests', element: <Contests /> },
      { path: '/contests/:slug', element: <ContestPage /> },
      { path: '/contests/:contestSlug/problems/:label', element: lazyPage(<Workspace />) },
      { path: '/submissions', element: <RequireAuth>{lazyPage(<Submissions />)}</RequireAuth> },
      { path: '/submissions/:id', element: <RequireAuth>{lazyPage(<SubmissionDetail />)}</RequireAuth> },
      { path: '/login', element: <Login /> },
      { path: '/register', element: <Register /> },
      { path: '/admin', element: <RequireAuth admin>{lazyPage(<AdminHome />)}</RequireAuth> },
      { path: '/admin/problems/:id', element: <RequireAuth admin>{lazyPage(<ProblemEditor />)}</RequireAuth> },
      { path: '/admin/contests/:id', element: <RequireAuth admin>{lazyPage(<ContestEditor />)}</RequireAuth> },
      { path: '*', element: <NotFound /> },
    ],
  },
];

export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        staleTime: 15_000,
        retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
        refetchOnWindowFocus: false,
      },
    },
  });
}

const router = createBrowserRouter(routes);
const queryClient = createQueryClient();

export function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <I18nProvider>
        <AuthProvider>
          <RouterProvider router={router} />
        </AuthProvider>
      </I18nProvider>
    </QueryClientProvider>
  );
}
