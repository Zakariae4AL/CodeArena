import Editor, { loader } from '@monaco-editor/react';
import * as monaco from 'monaco-editor';
import EditorWorker from 'monaco-editor/editor/editor.worker?worker';
import TsWorker from 'monaco-editor/languages/features/typescript/ts.worker?worker';
import { useEffect, useState } from 'react';
import { monacoLanguage } from '../lib/templates';
import type { CodeEditorProps } from './CodeEditor';

// Use the bundled Monaco instead of downloading it from a CDN.
self.MonacoEnvironment = {
  getWorker(_id: string, label: string) {
    return label === 'typescript' || label === 'javascript' ? new TsWorker() : new EditorWorker();
  },
};
loader.config({ monaco });

function currentTheme() {
  return document.documentElement.dataset.theme === 'light' ? 'light' : 'vs-dark';
}

export default function MonacoEditor({ value, language, onChange, readOnly, height = '100%' }: CodeEditorProps) {
  const [theme, setTheme] = useState(currentTheme);

  // Follow the app theme.
  useEffect(() => {
    const observer = new MutationObserver(() => setTheme(currentTheme()));
    observer.observe(document.documentElement, { attributes: true, attributeFilter: ['data-theme'] });
    return () => observer.disconnect();
  }, []);

  return (
    <Editor
      height={height}
      language={monacoLanguage[language] ?? 'plaintext'}
      value={value}
      theme={theme}
      onChange={(v) => onChange?.(v ?? '')}
      options={{
        readOnly,
        fontSize: 14,
        fontFamily: "'JetBrains Mono', 'Cascadia Code', ui-monospace, Menlo, Consolas, monospace",
        minimap: { enabled: false },
        scrollBeyondLastLine: false,
        automaticLayout: true,
        tabSize: 4,
        padding: { top: 12 },
        renderLineHighlight: 'gutter',
      }}
    />
  );
}
