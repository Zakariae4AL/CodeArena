import { lazy, Suspense } from 'react';

export interface CodeEditorProps {
  value: string;
  language: string;
  onChange?: (value: string) => void;
  readOnly?: boolean;
  height?: string;
}

// Monaco is big: it is only downloaded when an editor is shown.
const MonacoEditor = lazy(() => import('./MonacoEditor'));

export function CodeEditor(props: CodeEditorProps) {
  return (
    <Suspense fallback={<div className="h-full min-h-64 animate-pulse rounded-lg bg-surface-2" />}>
      <MonacoEditor {...props} />
    </Suspense>
  );
}
