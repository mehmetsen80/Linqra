import React, { useEffect, useState } from 'react';
import mermaid from 'mermaid';
import type { ArchitectureSlideContent } from '../schemas';
import { renderText } from '../utils';

export interface ArchitectureSlideProps {
  content: ArchitectureSlideContent;
}

export const ArchitectureSlide: React.FC<ArchitectureSlideProps> = ({ content }) => {
  const [svgContent, setSvgContent] = useState<string>('');

  useEffect(() => {
    mermaid.initialize({
      startOnLoad: false,
      theme: 'base',
      themeVariables: {
        fontFamily: 'Inter, sans-serif',
        primaryColor: '#f8fafc',
        primaryBorderColor: '#cbd5e1',
        primaryTextColor: '#1e293b',
        secondaryColor: '#e0f2fe',
        secondaryBorderColor: '#38bdf8',
        secondaryTextColor: '#0ea5e9',
        tertiaryColor: '#fef3c7',
        tertiaryBorderColor: '#fbbf24',
        tertiaryTextColor: '#d97706',
        lineColor: '#94a3b8',
      }
    });
    
    let isMounted = true;
    const renderDiagram = async () => {
      try {
        const id = `mermaid-${Math.random().toString(36).substring(2, 9)}`;
        const { svg } = await mermaid.render(id, content.mermaidCode);
        if (isMounted) {
          setSvgContent(svg);
        }
      } catch (err) {
        console.error("Mermaid rendering failed:", err);
      }
    };
    
    if (content.mermaidCode) {
      renderDiagram();
    }
    
    return () => { isMounted = false; };
  }, [content.mermaidCode]);

  const hasSidebar = !!content.description;
  const position = content.descriptionPosition || 'right';

  const getFlexDirection = () => {
    switch (position) {
      case 'left': return 'row-reverse';
      case 'top': return 'column-reverse';
      case 'bottom': return 'column';
      case 'right':
      default: return 'row';
    }
  };

  return (
    <div style={{ width: '100%', height: '100%', flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', padding: 'clamp(1.5rem, 4cqmin, 3rem)' }}>
      {(content.title || content.subtitle) && (
        <div style={{ marginBottom: '1.5rem' }}>
          {content.title && renderText(content.title, { fontSize: 'clamp(1.5rem, 4cqmin, 2.5rem)', fontWeight: 700, margin: 0, color: '#1e293b' }, 'h2')}
          {content.subtitle && renderText(content.subtitle, { fontSize: 'clamp(1rem, 2cqmin, 1.25rem)', margin: '0.5rem 0 0 0', color: '#64748b' }, 'p')}
        </div>
      )}

      <div style={{ flex: 1, display: 'flex', flexDirection: getFlexDirection(), minHeight: 0, gap: '2rem' }}>
        {/* Diagram Area */}
        <div style={{
          flex: hasSidebar ? 2 : 1,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: '#ffffff',
          borderRadius: '12px',
          border: '1px solid #e2e8f0',
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03)',
          overflow: 'hidden',
          padding: '1rem'
        }}>
          {svgContent ? (
            <div 
              style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}
              dangerouslySetInnerHTML={{ __html: svgContent }} 
            />
          ) : (
            <div style={{ color: '#94a3b8', fontSize: '1rem' }}>Rendering architecture...</div>
          )}
        </div>

        {/* Optional Sidebar */}
        {hasSidebar && (
          <div style={{ flex: 1, display: 'flex', flexDirection: 'column', overflowY: 'auto' }}>
            <div style={{
              background: '#f8fafc',
              borderRadius: '12px',
              padding: 'clamp(1rem, 3cqmin, 2rem)',
              border: '1px solid #e2e8f0',
              height: '100%'
            }}>
              {renderText(content.description, { color: '#475569', fontSize: 'clamp(0.9rem, 2cqmin, 1.1rem)', lineHeight: 1.6, margin: 0 }, 'div')}
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
