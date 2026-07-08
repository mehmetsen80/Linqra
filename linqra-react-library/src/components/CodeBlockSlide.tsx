import React from 'react';
import Prism from 'prismjs';
// Import essential languages
import 'prismjs/components/prism-javascript';
import 'prismjs/components/prism-typescript';
import 'prismjs/components/prism-jsx';
import 'prismjs/components/prism-tsx';
import 'prismjs/components/prism-java';
import 'prismjs/components/prism-python';
import 'prismjs/components/prism-css';
import 'prismjs/components/prism-json';
import 'prismjs/components/prism-bash';
import 'prismjs/components/prism-markdown';
import 'prismjs/components/prism-markup'; // HTML/XML

// Import default dark theme
import 'prismjs/themes/prism-tomorrow.css';

import type { CodeBlockContent } from '../schemas';

import { renderText } from '../utils';

interface Props {
  content: CodeBlockContent;
}

export const CodeBlockSlide: React.FC<Props> = ({ content }) => {
  // Ensure we have a valid grammar for the language, fallback to plain text if not loaded
  const grammar = Prism.languages[content.language] || Prism.languages.markup;
  const highlightedHtml = Prism.highlight(content.code, grammar, content.language);

  const defaultContainerStyle: React.CSSProperties = {
    flex: 1,
    minHeight: 0,
    padding: 'clamp(0.5rem, 2cqmin, 1.5rem)',
    borderRadius: '12px',
    boxShadow: '0 4px 20px rgba(0,0,0,0.15)',
    overflow: 'auto',
    margin: 0,
    fontSize: 'clamp(0.7rem, 2cqmin, 1.5rem)',
    lineHeight: 1.5,
    fontFamily: '"Fira Code", "Consolas", monospace',
    ...content.containerStyle
  };

  return (
    <div
      style={{
        containerType: 'size',
        flex: 1,
        minHeight: 0,
        display: 'flex',
        flexDirection: 'column',
        boxSizing: 'border-box',
        padding: 'var(--slide-padding, clamp(0.5rem, 3cqmin, 2rem))',
        fontFamily: 'var(--font-family)',
      }}
    >
      {content.title && renderText(content.title, { margin: '0 0 clamp(0.5rem, 2cqmin, 1rem) 0', color: 'var(--text-main)', fontSize: 'min(var(--title-font-size, clamp(1.2rem, 5cqmin, 2.5rem)), 8cqmin)', fontWeight: 'var(--title-font-weight, 700)', textAlign: 'var(--title-align, left)' as any }, 'h1')}
      {content.subtitle && renderText(content.subtitle, { margin: '0 0 var(--content-gap, 1rem) 0', color: 'var(--text-secondary)', fontSize: 'min(var(--body-font-size, clamp(0.8rem, 2.5cqmin, 1.1rem)), 3cqmin)' }, 'p')}
      
      <pre style={defaultContainerStyle} className={`language-${content.language}`}>
        <code 
          className={`language-${content.language}`} 
          style={{ display: 'block', paddingBottom: '0.5rem' }}
          dangerouslySetInnerHTML={{ __html: highlightedHtml }}
        />
      </pre>
    </div>
  );
};
