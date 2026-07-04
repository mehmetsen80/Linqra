import React from 'react';
import type { StandardContentSlideContent } from '../schemas';
import { renderImage, renderText } from '../utils';

interface Props {
  content: StandardContentSlideContent;
}

export const StandardContentSlide: React.FC<Props> = ({ content }) => {
  const renderList = (items: NonNullable<StandardContentSlideContent['listItems']>) => {
    return (
      <ul style={{ paddingLeft: '2rem', margin: '1rem 0', color: 'var(--text-secondary)' }}>
        {items.map((item: any, idx) => {
          if (typeof item === 'string' || !('subItems' in item)) {
            // It's a string OR an object that is just {text, style} (TextContent)
            return renderText(item, { marginBottom: '0.75rem', fontSize: 'var(--body-font-size)', lineHeight: 'var(--body-line-height)' }, 'li', idx);
          }
          // It's the complex list item structure with subItems
          return (
            <li key={idx} style={{ marginBottom: '0.75rem', fontSize: 'var(--body-font-size)', lineHeight: 'var(--body-line-height)', ...item.style }}>
              {renderText(item.text, {}, 'span')}
              {item.subItems && item.subItems.length > 0 && (
                <ul style={{ paddingLeft: '2rem', marginTop: '0.5rem' }}>
                  {item.subItems.map((sub: any, subIdx: number) => (
                    <React.Fragment key={subIdx}>
                      {renderText(sub, { marginBottom: '0.5rem', fontSize: 'calc(var(--body-font-size) * 0.85)' }, 'li')}
                    </React.Fragment>
                  ))}
                </ul>
              )}
            </li>
          );
        })}
      </ul>
    );
  };

  return (
    <div style={{ containerType: 'size', padding: 'clamp(1rem, 5cqmin, 4rem)', height: '100%', display: 'flex', flexDirection: 'column', boxSizing: 'border-box', fontFamily: 'var(--font-family)', letterSpacing: 'var(--letter-spacing)' }}>
      {renderText(content.title, { marginBottom: 'clamp(1rem, 3cqmin, 2rem)', color: 'var(--text-main)', fontSize: 'var(--title-font-size)', fontWeight: 'var(--title-font-weight)', textAlign: 'var(--title-align)' as any, borderBottom: '2px solid var(--card-border)', paddingBottom: '1rem', lineHeight: 1.2, flexShrink: 0 }, 'h1')}
      <div style={{ flex: 1, minHeight: 0, overflowY: 'auto', display: 'flex', flexDirection: 'column', paddingRight: '1rem' }}>
        {renderImage(content.image)}
        {renderText(content.bodyText, { fontSize: 'var(--body-font-size)', color: 'var(--text-secondary)', lineHeight: 'var(--body-line-height)', textAlign: 'var(--body-align)' as any, marginBottom: '2rem', whiteSpace: 'pre-wrap' }, 'p')}
        {content.listItems && content.listItems.length > 0 && renderList(content.listItems)}
      </div>
    </div>
  );
};
