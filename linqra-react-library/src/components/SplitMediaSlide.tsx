import React from 'react';
import type { SplitMediaSlideContent } from '../schemas';
import { renderImage, renderText } from '../utils';

interface Props {
  content: SplitMediaSlideContent;
}

export const SplitMediaSlide: React.FC<Props> = ({ content }) => {
  const isTextRight = content.layoutDirection === 'text_right';

  const containerId = React.useId().replace(/:/g, '');

  const textSection = (
    <div className={`text-section-${containerId}`} style={{ containerType: 'inline-size', padding: 'clamp(1rem, 5cqi, 3rem)', display: 'flex', flexDirection: 'column', overflowY: 'auto' }}>
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
    <div className={`media-section-${containerId}`} style={{ padding: 'clamp(1rem, 3cqmin, 2rem)', display: 'flex', flexDirection: 'column', alignItems: 'center', background: 'var(--card-bg)', overflow: 'hidden' }}>
      {content.media?.type === 'image' && (
        <img 
          src={content.media.source} 
          alt={typeof content.media.caption === 'string' ? content.media.caption : (typeof content.media.caption === 'object' && content.media.caption !== null ? (content.media.caption as any).text : 'Slide media')} 
          style={{ width: '100%', height: 'auto', maxHeight: '70vh', objectFit: 'contain', borderRadius: '8px', boxShadow: '0 4px 12px rgba(0,0,0,0.2)' }} 
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
    <div className={`split-container-${containerId}`} style={{ containerType: 'inline-size', containerName: 'splitMedia', padding: 'clamp(1rem, 5cqi, 3rem) clamp(1rem, 6cqi, 4rem)', height: '100%', display: 'flex', flexDirection: 'column', boxSizing: 'border-box' }}>
      <style>{`
        .split-content-${containerId} {
          flex: 1;
          display: flex;
          flex-direction: row;
          gap: 2rem;
          overflow: hidden;
        }
        .text-section-${containerId} {
          flex: 1;
          justify-content: center;
          min-height: 0;
        }
        .media-section-${containerId} {
          flex: 1;
          justify-content: center;
          min-height: 0;
        }
        @container splitMedia (max-width: 800px) {
          .split-content-${containerId} {
            flex-direction: column;
            overflow-y: auto;
          }
          .text-section-${containerId} {
            flex: 0 0 auto;
            justify-content: flex-start;
          }
          .media-section-${containerId} {
            flex: 0 0 auto;
            justify-content: flex-start;
          }
        }
      `}</style>
      {renderText(content.title, { marginBottom: 'clamp(1rem, 3cqi, 2rem)', color: 'var(--text-main)', fontSize: 'clamp(2rem, 6cqi, 3rem)', flexShrink: 0 }, 'h1')}
      {renderImage(content.image)}
      <div className={`split-content-${containerId}`}>
        {isTextRight ? mediaSection : textSection}
        {isTextRight ? textSection : mediaSection}
      </div>
    </div>
  );
};
