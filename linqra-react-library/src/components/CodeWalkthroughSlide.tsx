import React from 'react';
import Prism from 'prismjs';
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
import 'prismjs/components/prism-markup';
import 'prismjs/themes/prism-tomorrow.css';

import type { CodeWalkthroughSlideContent } from '../schemas';
import { renderText } from '../utils';

export interface CodeWalkthroughSlideProps {
  content: CodeWalkthroughSlideContent;
}

export const CodeWalkthroughSlide: React.FC<CodeWalkthroughSlideProps> = ({ content }) => {
  const { title, subtitle, code, language, filename, annotations = [], activeAnnotationId, layout = 'row' } = content;
  const containerId = React.useId().replace(/:/g, '');

  const grammar = Prism.languages[language] || Prism.languages.markup;
  const highlightedHtml = Prism.highlight(code, grammar, language);
  
  const lineHeight = 1.6; // em
  const activeAnnotation = annotations.find(a => a.id === activeAnnotationId);

  return (
    <div style={{ width: '100%', height: '100%', flex: 1, display: 'flex', flexDirection: 'column', padding: 'clamp(1rem, 3cqmin, 2rem)', boxSizing: 'border-box' }}>
      
      <style>{`
        .walkthrough-container-${containerId} {
          container-type: inline-size;
          container-name: walkthroughGrid;
          width: 100%;
          flex: 1;
          min-height: 0;
          display: grid;
          grid-template-columns: ${layout === 'row' ? '60% 1fr' : layout === 'row-reverse' ? '1fr 60%' : '1fr'};
          grid-auto-rows: ${layout === 'column' || layout === 'column-reverse' ? 'minmax(0, 1fr)' : '1fr'};
          gap: clamp(1.5rem, 4cqmin, 3rem);
        }

        .ide-pane-${containerId} {
          display: flex;
          flex-direction: column;
          min-width: 0;
          min-height: 0;
          background: #0f172a;
          border-radius: 16px;
          border: 1px solid #334155;
          box-shadow: 0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04);
          overflow: hidden;
          align-self: start;
          max-height: 100%;
          grid-column: ${layout === 'row-reverse' ? '2' : '1'};
          grid-row: ${layout === 'column-reverse' ? '2' : '1'};
        }

        .sidebar-pane-${containerId} {
          display: flex;
          flex-direction: column;
          gap: 1rem;
          min-width: 0;
          overflow-y: auto;
          overflow-x: visible; /* Prevent clipping borders */
          align-self: start;
          max-height: 100%;
          padding: 0.5rem; /* Give room for focus shadows/transforms */
          margin: -0.5rem; /* Offset padding to align visually */
          grid-column: ${layout === 'row' ? '2' : '1'};
          grid-row: ${layout === 'column' ? '2' : '1'};
        }

        /* Portrait / Narrow mode */
        @container walkthroughGrid (max-width: 800px) {
          .walkthrough-container-${containerId} {
            grid-template-columns: 1fr;
            grid-auto-rows: auto;
          }
          .ide-pane-${containerId} {
            grid-column: 1;
            grid-row: ${layout === 'row-reverse' || layout === 'column-reverse' ? '2' : '1'};
            height: 400px;
          }
          .sidebar-pane-${containerId} {
            grid-column: 1;
            grid-row: ${layout === 'row-reverse' || layout === 'column-reverse' ? '1' : '2'};
          }
        }
      `}</style>

      {(title || subtitle) && (
        <div style={{ marginBottom: 'clamp(1rem, 2cqmin, 2rem)', textAlign: 'left', flexShrink: 0 }}>
          {title && renderText(title, { fontSize: 'clamp(1.75rem, 5cqmin, 3.5rem)', fontWeight: 700, margin: 0, color: '#1e293b' }, 'h2')}
          {subtitle && renderText(subtitle, { fontSize: 'clamp(1.1rem, 2.5cqmin, 1.5rem)', margin: '0.75rem 0 0 0', color: '#64748b' }, 'p')}
        </div>
      )}

      <div className={`walkthrough-container-${containerId}`}>
        
        {/* IDE Pane */}
        <div className={`ide-pane-${containerId}`}>
          {/* Mac window header */}
          <div style={{ display: 'flex', alignItems: 'center', padding: '0.75rem 1rem', background: '#1e293b', borderBottom: '1px solid #334155' }}>
            <div style={{ display: 'flex', gap: '6px' }}>
              <div style={{ width: 12, height: 12, borderRadius: '50%', background: '#ef4444' }} />
              <div style={{ width: 12, height: 12, borderRadius: '50%', background: '#f59e0b' }} />
              <div style={{ width: 12, height: 12, borderRadius: '50%', background: '#10b981' }} />
            </div>
            {filename && (
              <div style={{ margin: '0 auto', transform: 'translateX(-24px)', color: '#94a3b8', fontSize: '0.85rem', fontFamily: 'monospace' }}>
                {filename}
              </div>
            )}
          </div>
          
          {/* Code Editor */}
          <div style={{ flex: 1, minHeight: 0, position: 'relative', overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
            <pre style={{
              flex: 1,
              margin: 0,
              padding: '1.5rem',
              background: 'transparent',
              overflow: 'hidden',
              whiteSpace: 'pre-wrap',
              wordBreak: 'break-word',
              position: 'relative',
              fontSize: 'clamp(0.85rem, 1.8cqmin, 1.1rem)',
              lineHeight: lineHeight,
              fontFamily: '"Fira Code", "Consolas", monospace',
              color: '#f8fafc',
            }} className={`language-${language}`}>
              
              {/* Highlight Overlay */}
              {activeAnnotation && (
                <div style={{
                  position: 'absolute',
                  top: `calc(1.5rem + ${(activeAnnotation.lineRange[0] - 1) * lineHeight}em)`,
                  left: 0,
                  right: 0,
                  width: '100%',
                  height: `${(activeAnnotation.lineRange[1] - activeAnnotation.lineRange[0] + 1) * lineHeight}em`,
                  background: 'rgba(56, 189, 248, 0.15)',
                  borderLeft: '4px solid #38bdf8',
                  zIndex: 0,
                  pointerEvents: 'none',
                  transition: 'top 0.3s ease, height 0.3s ease'
                }} />
              )}

              <code
                className={`language-${language}`}
                style={{ 
                  position: 'relative', 
                  zIndex: 1, 
                  display: 'block',
                  opacity: activeAnnotation ? 0.9 : 1
                }}
                dangerouslySetInnerHTML={{ __html: highlightedHtml }}
              />
            </pre>
          </div>
        </div>

        {/* Sidebar Pane */}
        {annotations.length > 0 && (
          <div className={`sidebar-pane-${containerId}`}>
            {annotations.map(annotation => {
              const isActive = annotation.id === activeAnnotationId;
              const isDimmed = activeAnnotationId && !isActive;
              
              return (
                <div key={annotation.id} style={{
                  background: isActive ? '#f8fafc' : '#ffffff',
                  border: isActive ? '2px solid #38bdf8' : '1px solid #e2e8f0',
                  borderRadius: '12px',
                  padding: 'clamp(1rem, 2cqmin, 1.5rem)',
                  opacity: isDimmed ? 0.6 : 1,
                  transform: isActive ? 'translateX(4px)' : 'translateX(0)',
                  transition: 'all 0.2s ease-in-out',
                  boxShadow: isActive ? '0 10px 15px -3px rgba(56, 189, 248, 0.1)' : '0 1px 2px 0 rgba(0, 0, 0, 0.05)'
                }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginBottom: '0.5rem' }}>
                    <div style={{
                      background: isActive ? '#38bdf8' : '#e2e8f0',
                      color: isActive ? '#ffffff' : '#64748b',
                      fontSize: '0.75rem',
                      fontWeight: 700,
                      padding: '2px 8px',
                      borderRadius: '12px',
                      fontFamily: 'monospace'
                    }}>
                      L{annotation.lineRange[0]}{annotation.lineRange[1] !== annotation.lineRange[0] ? `-${annotation.lineRange[1]}` : ''}
                    </div>
                    {annotation.title && (
                      <h4 style={{ margin: 0, fontSize: 'clamp(1rem, 2cqmin, 1.15rem)', fontWeight: 600, color: '#1e293b' }}>
                        {annotation.title}
                      </h4>
                    )}
                  </div>
                  <p style={{ margin: 0, fontSize: 'clamp(0.85rem, 1.8cqmin, 1rem)', lineHeight: 1.5, color: '#475569' }}>
                    {annotation.description}
                  </p>
                </div>
              );
            })}
          </div>
        )}

      </div>
    </div>
  );
};
