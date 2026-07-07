import React from 'react';
import type { MediaColumnGridSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: MediaColumnGridSlideContent;
}

export const MediaColumnGridSlide: React.FC<Props> = ({ content }) => {
  const { title, subtitle, columns } = content;

  const renderText = (textObj: any, defaultStyle: React.CSSProperties = {}, Tag: any = 'span') => {
    if (!textObj) return null;
    if (typeof textObj === 'string') return <Tag style={defaultStyle}>{textObj}</Tag>;
    return <Tag style={{ ...defaultStyle, ...(textObj.style || {}) }}>{textObj.text}</Tag>;
  };

  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      padding: '2.5rem 3rem',
      boxSizing: 'border-box'
    }}>
      {/* Header Section */}
      {(title || subtitle) && (
        <div style={{ marginBottom: '2.5rem', textAlign: 'center' }}>
          {renderText(title, {
            fontSize: 'clamp(1.5rem, 4cqmin, 2.5rem)',
            fontWeight: 800,
            color: '#0f172a',
            margin: '0 0 1rem 0',
            lineHeight: 1.2
          }, 'h2')}
          {renderText(subtitle, {
            fontSize: 'clamp(1rem, 2cqmin, 1.25rem)',
            color: '#64748b',
            margin: 0,
            lineHeight: 1.5,
            maxWidth: '800px',
            marginLeft: 'auto',
            marginRight: 'auto'
          }, 'p')}
        </div>
      )}

      {/* Columns Grid */}
      <div style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'row',
        gap: '2.5rem',
        minHeight: 0 // allow flex children to shrink
      }}>
        {columns.map((col, idx) => (
          <div key={idx} style={{
            flex: 1,
            display: 'flex',
            flexDirection: 'column',
            gap: '1.5rem',
            minWidth: 0
          }}>
            {/* Column Header */}
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
              {col.icon && (
                <div style={{
                  width: '40px',
                  height: '40px',
                  borderRadius: '10px',
                  background: col.color ? `${col.color}15` : '#f1f5f9',
                  color: col.color || '#3b82f6',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0
                }}>
                  <DynamicIcon name={col.icon} size={24} />
                </div>
              )}
              {renderText(col.heading, {
                margin: 0,
                fontSize: '1.25rem',
                fontWeight: 700,
                color: '#1e293b'
              }, 'h3')}
            </div>

            {/* Bullets */}
            {col.bullets && col.bullets.length > 0 && (
              <ul style={{
                margin: 0,
                padding: '0 0 0 1.25rem',
                color: '#475569',
                display: 'flex',
                flexDirection: 'column',
                gap: '0.5rem',
                fontSize: '0.95rem'
              }}>
                {col.bullets.map((bullet, bIdx) => (
                  <li key={bIdx}>{renderText(bullet)}</li>
                ))}
              </ul>
            )}

            {/* Media / Screenshot Container */}
            {col.mediaUrl && (
              <div style={{
                flex: 1,
                marginTop: '0.5rem',
                borderRadius: '12px',
                overflow: 'hidden',
                boxShadow: '0 10px 25px -5px rgba(0, 0, 0, 0.1), 0 8px 10px -6px rgba(0, 0, 0, 0.1)',
                border: '1px solid #e2e8f0',
                background: '#f8fafc',
                display: 'flex',
                flexDirection: 'column'
              }}>
                {/* Mock Browser/Device Header Bar */}
                <div style={{
                  height: '24px',
                  background: '#f1f5f9',
                  borderBottom: '1px solid #e2e8f0',
                  display: 'flex',
                  alignItems: 'center',
                  padding: '0 12px',
                  gap: '6px'
                }}>
                  <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#cbd5e1' }} />
                  <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#cbd5e1' }} />
                  <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#cbd5e1' }} />
                </div>
                
                {/* Image */}
                <div style={{
                  flex: 1,
                  backgroundImage: `url(${col.mediaUrl})`,
                  backgroundSize: 'cover',
                  backgroundPosition: 'top center',
                  backgroundRepeat: 'no-repeat'
                }} />
              </div>
            )}
          </div>
        ))}
      </div>
    </div>
  );
};
