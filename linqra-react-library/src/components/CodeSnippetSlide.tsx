import React, { useEffect, useRef } from 'react';
import Prism from 'prismjs';
import 'prismjs/themes/prism-tomorrow.css';
import 'prismjs/components/prism-javascript';
import 'prismjs/components/prism-typescript';
import 'prismjs/components/prism-json';
import 'prismjs/components/prism-css';
import 'prismjs/components/prism-python';
import 'prismjs/components/prism-bash';
import 'prismjs/components/prism-sql';
import type { CodeSnippetSlideContent } from '../schemas';

interface Props {
  content: CodeSnippetSlideContent;
}

export const CodeSnippetSlide: React.FC<Props> = ({ content }) => {
  const codeRef = useRef<HTMLElement>(null);
  const language = content.language || 'typescript';

  useEffect(() => {
    if (codeRef.current) {
      Prism.highlightElement(codeRef.current);
    }
  }, [content.code, language]);

  return (
    <div style={{
      width: '100%',
      borderRadius: '8px',
      overflow: 'hidden',
      background: '#2d2d2d',
      boxShadow: 'inset 0 0 0 1px rgba(255,255,255,0.1)',
      ...(content.containerStyle || {})
    }}>
      <pre style={{
        margin: 0,
        padding: '1.2cqi 1.5cqi',
        overflowX: 'auto',
        fontSize: '0.9cqi',
        fontFamily: "'Fira Code', 'JetBrains Mono', 'Menlo', 'Monaco', 'Courier New', monospace",
        lineHeight: 1.5
      }}>
        <code 
          ref={codeRef} 
          className={`language-${language}`}
          style={{ whiteSpace: 'pre' }}
        >
          {content.code}
        </code>
      </pre>
    </div>
  );
};
