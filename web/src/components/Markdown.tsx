import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

/** Problem statements. react-markdown never renders raw HTML, so statements cannot inject scripts. */
export function Markdown({ children }: { children: string }) {
  return (
    <div className="prose-statement text-[0.95rem]">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{children}</ReactMarkdown>
    </div>
  );
}
