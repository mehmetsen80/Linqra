import React from 'react';
import type { SplitMediaSlideContent } from '../schemas';
import { renderImage, renderText } from '../utils';

interface Props {
  content: SplitMediaSlideContent;
}

export const SplitMediaSlide: React.FC<Props> = ({ content }) => {
  const isTextRight = content.layoutDirection === 'text_right';

  const textSection = (
    <div style={{ flex: 1, minHeight: 0, containerType: 'inline-size', padding: 'clamp(1rem, 5cqi, 3rem)', display: 'flex', flexDirection: 'column', justifyContent: 'center', overflowY: 'auto' }}>
      {content.text?.heading && renderText(content.text.heading, { fontSize: 'clamp(1.5rem, 8cqi, 2.5rem)', color: 'var(--text-main)', marginBottom: '1.5rem', lineHeight: 1.2 }, 'h2')}
      {content.text?.body && renderText(content.text.body, { fontSize: 'clamp(1rem, 4cqi, 1.5rem)', color: 'var(--text-secondary)', lineHeight: 1.6 }, 'p')}
      {content.text?.bullets && content.text.bullets.length > 0 && (
        <ul style={{ paddingLeft: '2rem', marginTop: '1rem', color: 'var(--text-secondary)' }}>
          {content.text.bullets.map((bullet, idx) => (
            <React.Fragment key={idx}>
              {renderText(bullet, { marginBottom: '0.5rem', fontSize: 'clamp(1rem, 4cqi, 1.2rem)' }, 'li')}
            </React.Fragment>
          ))}
        </ul>
      )}
    </div>
  );

  const mediaSection = (
    <div style={{ flex: 1, minHeight: 0, padding: 'clamp(1rem, 3cqmin, 2rem)', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', background: 'var(--card-bg)', overflow: 'hidden' }}>
      {content.media?.type === 'image' && (
        <img 
          src={content.media.source} 
          alt={typeof content.media.caption === 'string' ? content.media.caption : (typeof content.media.caption === 'object' && content.media.caption !== null ? (content.media.caption as any).text : 'Slide media')} 
          style={{ maxWidth: '100%', maxHeight: '70vh', objectFit: 'contain', borderRadius: '8px', boxShadow: '0 4px 12px rgba(0,0,0,0.2)' }} 
        />
      )}
      {content.media?.type !== 'image' && (
        <div style={{ width: '100%', height: '300px', display: 'flex', alignItems: 'center', justifyContent: 'center', border: '2px dashed var(--card-border)', borderRadius: '8px', color: 'var(--text-tertiary)' }}>
          [{content.media?.type.toUpperCase()} PLACEHOLDER: {content.media?.source}]
        </div>
      )}
      {content.media?.caption && renderText(content.media.caption, { marginTop: '1rem', color: 'var(--text-tertiary)', fontSize: '1rem', fontStyle: 'italic' }, 'p')}
    </div>
  );

  return (
    <div style={{ containerType: 'inline-size', padding: 'clamp(1rem, 5cqi, 3rem) clamp(1rem, 6cqi, 4rem)', height: '100%', display: 'flex', flexDirection: 'column', boxSizing: 'border-box' }}>
      {renderText(content.title, { marginBottom: 'clamp(1rem, 3cqi, 2rem)', color: 'var(--text-main)', fontSize: 'clamp(2rem, 6cqi, 3rem)' }, 'h1')}
      {renderImage(content.image)}
      <div style={{ flex: 1, display: 'flex', gap: '2rem', overflow: 'hidden' }}>
        {isTextRight ? mediaSection : textSection}
        {isTextRight ? textSection : mediaSection}
      </div>
    </div>
  );
};
