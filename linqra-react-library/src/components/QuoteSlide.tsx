import React from 'react';
import type { QuoteSlideContent } from '../schemas';

interface Props {
  content: QuoteSlideContent;
}

export const QuoteSlide: React.FC<Props> = ({ content }) => {
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: '1cqi',
      padding: '1.5cqi 0',
      borderLeft: '4px solid var(--text-main, #333)',
      paddingLeft: '2.5cqi',
      boxSizing: 'border-box',
      ...(content.containerStyle || {})
    }}>
      <div style={{
        fontSize: '2cqi',
        fontStyle: 'italic',
        lineHeight: 1.5,
        color: 'var(--text-main, #333)',
      }}>
        "{content.quote}"
      </div>
      {(content.author || content.role) && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2cqi' }}>
          {content.author && (
            <span style={{ fontSize: '1.2cqi', fontWeight: 600, color: 'var(--text-main, #111)' }}>
              {content.author}
            </span>
          )}
          {content.role && (
            <span style={{ fontSize: '1cqi', color: 'var(--text-secondary, #666)' }}>
              {content.role}
            </span>
          )}
        </div>
      )}
    </div>
  );
};
