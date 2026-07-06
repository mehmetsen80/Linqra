import React from 'react';
import type { TextBoxProcessSlideContent } from '../schemas';

interface Props {
  content: TextBoxProcessSlideContent;
}

export const TextBoxProcessSlide: React.FC<Props> = ({ content }) => {
  const steps = content.steps || [];

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
      padding: '2rem 3rem',
      boxSizing: 'border-box'
    }}>
      {/* Title Section */}
      {(content.title || content.subtitle) && (
        <div style={{ display: 'flex', alignItems: 'center', marginBottom: '3rem' }}>
          {/* Target Icon */}
          <div style={{
            marginRight: '1rem',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            width: '40px',
            height: '40px',
            borderRadius: '50%',
            border: '2px solid #ccc',
            position: 'relative'
          }}>
            <div style={{
              width: '24px',
              height: '24px',
              borderRadius: '50%',
              border: '2px solid #ccc',
            }} />
            <div style={{
              position: 'absolute',
              left: '-20px',
              width: '20px',
              height: '2px',
              backgroundColor: '#ccc'
            }} />
          </div>
          <div>
            {content.title && renderText(content.title, { fontSize: '2.5rem', fontWeight: 800, color: '#111', margin: 0 }, 'h1')}
            {content.subtitle && renderText(content.subtitle, { fontSize: '1.2rem', color: '#666', marginTop: '0.25rem' }, 'p')}
          </div>
        </div>
      )}

      {/* Columns Container */}
      <div style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'row',
        gap: '2rem',
        alignItems: 'stretch'
      }}>
        {steps.map((step, idx) => {
          const color = step.color || '#3b82f6';
          
          return (
            <div key={idx} style={{
              flex: 1,
              display: 'flex',
              flexDirection: 'column',
              position: 'relative'
            }}>
              
              {/* Ribbon Header Container */}
              <div style={{
                position: 'relative',
                display: 'flex',
                height: '70px',
                zIndex: 2,
                marginRight: '30px' // Space for chevron
              }}>
                {/* 3D Fold (Darker triangle behind) */}
                <div style={{
                  position: 'absolute',
                  left: 0,
                  bottom: '-14px',
                  width: 0,
                  height: 0,
                  borderTop: `14px solid #000000`, // Black base for multiplying/darkening
                  borderLeft: `14px solid transparent`,
                  opacity: 0.25, // Lowered opacity so it matches the reference shadow better
                  zIndex: -1
                }} />

                {/* Left Number Block */}
                <div style={{
                  backgroundColor: color,
                  filter: 'brightness(0.8)',
                  width: '70px',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'white',
                  fontSize: '1.5rem',
                  fontWeight: 800
                }}>
                  {step.stepLabel || `0${idx + 1}`}
                </div>

                {/* Main Ribbon Body */}
                <div style={{
                  flex: 1,
                  backgroundColor: color,
                  display: 'flex',
                  alignItems: 'center',
                  paddingLeft: '1rem',
                  color: 'white',
                  fontSize: '1.2rem',
                  fontWeight: 700,
                  textTransform: 'uppercase'
                }}>
                  {renderText(step.title)}
                </div>

                {/* Chevron Arrow Head using CSS borders */}
                <div style={{
                  position: 'absolute',
                  right: '-30px',
                  top: 0,
                  width: 0,
                  height: 0,
                  borderTop: '35px solid transparent',
                  borderBottom: '35px solid transparent',
                  borderLeft: `30px solid ${color}`,
                }} />
              </div>

              {/* Card Body */}
              <div style={{
                flex: 1,
                backgroundColor: '#ffffff',
                border: '1px solid #e5e7eb',
                borderTop: 'none',
                marginLeft: '14px', // Offset from the 3D fold
                marginRight: '30px', // Match ribbon width
                padding: '2rem 1.5rem',
                boxShadow: '4px 8px 24px rgba(0,0,0,0.08)',
                zIndex: 1,
                position: 'relative'
              }}>
                {Array.isArray(step.description) ? (
                  step.description.map((paragraph, pIdx) => (
                    <div key={pIdx} style={{ marginBottom: pIdx === step.description.length - 1 ? 0 : '1.5rem' }}>
                      {renderText(paragraph, { color: '#64748b', fontSize: '0.95rem', lineHeight: 1.6, margin: 0 }, 'p')}
                    </div>
                  ))
                ) : (
                  renderText(step.description, { color: '#64748b', fontSize: '0.95rem', lineHeight: 1.6, margin: 0 }, 'p')
                )}
              </div>

            </div>
          );
        })}
      </div>
    </div>
  );
};
