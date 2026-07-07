import React from 'react';
import type { MediaAnnotatorSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: MediaAnnotatorSlideContent;
}

export const MediaAnnotatorSlide: React.FC<Props> = ({ content }) => {
  const { title, subtitle, mediaUrl, imagePosition = 'left', steps, lineStyle, containerStyle = {}, contentStyle = {} } = content;

  const renderText = (textObj: any, defaultStyle: React.CSSProperties = {}, Tag: any = 'span') => {
    if (!textObj) return null;
    if (typeof textObj === 'string') return <Tag style={defaultStyle}>{textObj}</Tag>;
    return <Tag style={{ ...defaultStyle, ...(textObj.style || {}) }}>{textObj.text}</Tag>;
  };

  const isLeft = imagePosition === 'left';

  const mediaSection = mediaUrl ? (
    <div style={{
      flex: 1,
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'flex-start',
      minWidth: 0,
      minHeight: 0 // CRITICAL: Allows flex container to shrink past content size
    }}>
      <div style={{
        width: '100%',
        height: '100%',
        flex: 1,
        minHeight: 0,
        borderRadius: '16px',
        overflow: 'hidden',
        boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
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
          gap: '6px',
          flexShrink: 0
        }}>
          <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#cbd5e1' }} />
          <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#cbd5e1' }} />
          <div style={{ width: '8px', height: '8px', borderRadius: '50%', background: '#cbd5e1' }} />
        </div>
        
        {/* Image Wrapper */}
        <div style={{ flex: 1, position: 'relative', minHeight: 0 }}>
          <img 
            src={mediaUrl} 
            alt="Annotated Media" 
            style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', objectFit: 'cover' }} 
          />
        </div>
      </div>
    </div>
  ) : null;

  const hasIcons = steps.some(s => s.icon);

  const stepsSection = (
    <div style={{
      flex: 1,
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'flex-start',
      minWidth: 0,
      minHeight: 0,
      padding: mediaUrl ? (isLeft ? '0 0 0 3rem' : '0 3rem 0 0') : '0'
    }}>
      {steps.map((step, idx) => {
        const isLast = idx === steps.length - 1;
        const color = step.color || '#3b82f6';
        const scale = step.nodeScale || 1;
        
        return (
          <div key={idx} style={{ 
            display: 'flex', 
            flexDirection: 'row', 
            alignItems: 'center', // Vertically center all items in the row
            marginBottom: isLast ? 0 : '2rem' 
          }}>
            
            {/* Left Icon Column (Fixed Width) */}
            {hasIcons && (
              <div style={{
                width: 'clamp(80px, 10cqmin, 112px)',
                display: 'flex',
                justifyContent: 'center',
                flexShrink: 0,
                marginRight: '1rem'
              }}>
                {step.icon && (
                  <div style={{
                    width: `calc(clamp(48px, 6cqmin, 56px) * ${scale})`,
                    height: `calc(clamp(48px, 6cqmin, 56px) * ${scale})`,
                    borderRadius: '50%',
                    backgroundColor: step.iconBg || `${color}15`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    zIndex: 2
                  }}>
                    <DynamicIcon name={step.icon} size={28 * scale} color={step.iconColor || color} />
                  </div>
                )}
              </div>
            )}

            {/* Number Column (Fixed Width) */}
            <div style={{ 
              width: '64px',
              display: 'flex',
              justifyContent: 'center',
              position: 'relative',
              alignSelf: 'stretch', // Stretch to row height so 50% top works properly
              flexShrink: 0
            }}>
              
              {/* Timeline Line */}
              {!isLast && (
                <div style={{
                  position: 'absolute',
                  top: '50%',
                  left: '50%',
                  transform: 'translateX(-50%)',
                  width: lineStyle?.thickness || '2px',
                  height: 'calc(100% + 2rem)', // Reaches to the center of the next row
                  borderLeft: `${lineStyle?.thickness || '2px'} ${lineStyle?.style || 'solid'} ${lineStyle?.color || `${color}40`}`,
                  zIndex: 1
                }} />
              )}

              {/* Step Number Circle */}
              <div style={{
                width: `${32 * scale}px`,
                height: `${32 * scale}px`,
                borderRadius: '50%',
                background: color,
                color: '#ffffff',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 700,
                fontSize: `${1 * scale}rem`,
                zIndex: 2,
                alignSelf: 'center', // Center vertically within the stretched column
                boxShadow: `0 0 0 ${4 * scale}px #ffffff, 0 4px 6px -1px ${color}60`
              }}>
                {idx + 1}
              </div>
            </div>

            {/* Step Content */}
            <div style={{ flex: 1, paddingLeft: '1.5rem', textAlign: 'left' }}>
              {renderText(step.title, {
                margin: '0 0 0.5rem 0',
                fontSize: '1.25rem',
                fontWeight: 700,
                color: '#1e293b'
              }, 'h3')}
              
              {renderText(step.description, {
                margin: 0,
                fontSize: '1rem',
                lineHeight: 1.6,
                color: '#475569'
              }, 'p')}
            </div>

          </div>
        );
      })}
    </div>
  );

  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      padding: 'clamp(2rem, 4cqmin, 4rem)',
      boxSizing: 'border-box',
      background: 'var(--slide-bg, #ffffff)',
      containerType: 'inline-size',
      ...containerStyle
    }}>
      {/* Header Section */}
      {(title || subtitle) && (
        <div style={{ marginBottom: '3rem' }}>
          {renderText(title, {
            fontSize: 'clamp(2rem, 5cqmin, 3rem)',
            fontWeight: 800,
            color: '#0f172a',
            margin: '0 0 1rem 0',
            lineHeight: 1.2,
            letterSpacing: '-0.02em'
          }, 'h2')}
          {renderText(subtitle, {
            fontSize: 'clamp(1.1rem, 2cqmin, 1.25rem)',
            color: '#64748b',
            margin: 0,
            lineHeight: 1.5,
            maxWidth: '80%'
          }, 'p')}
        </div>
      )}

      {/* Main Split Section */}
      <div style={{
        flex: 1,
        display: 'flex',
        gap: '4rem',
        alignItems: 'flex-start',
        flexDirection: isLeft ? 'row' : 'row-reverse',
        minHeight: 0,
        ...contentStyle
      }}>
        {mediaUrl ? (
          isLeft ? (
            <>
              {mediaSection}
              {stepsSection}
            </>
          ) : (
            <>
              {stepsSection}
              {mediaSection}
            </>
          )
        ) : (
          <div style={{ maxWidth: '800px', width: '100%' }}>
            {stepsSection}
          </div>
        )}
      </div>
    </div>
  );
};
