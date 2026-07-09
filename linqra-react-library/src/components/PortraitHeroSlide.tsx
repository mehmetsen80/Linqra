import React from 'react';
import { DynamicIcon } from './DynamicIcon';
import type { PortraitHeroSlideContent } from '../schemas';

interface Props {
  content: PortraitHeroSlideContent;
}

export const PortraitHeroSlide: React.FC<Props> = ({ content }) => {
  const { title, subtitle, eyebrow, mediaUrls, background, mediaLayout = 'collage', imageFit = 'cover' } = content;

  const renderText = (textObj: any, defaultStyle: React.CSSProperties = {}, Tag: any = 'span') => {
    if (!textObj) return null;
    if (typeof textObj === 'string') return <Tag style={defaultStyle}>{textObj}</Tag>;
    
    if (textObj.icon) {
      const isRight = textObj.iconPosition === 'right';
      return (
        <Tag style={{ 
          ...defaultStyle, 
          ...(textObj.style || {}),
          display: 'inline-flex', 
          alignItems: 'center', 
          gap: '0.375rem' 
        }}>
          {!isRight && <DynamicIcon name={textObj.icon} size={16} />}
          <span>{textObj.text}</span>
          {isRight && <DynamicIcon name={textObj.icon} size={16} />}
        </Tag>
      );
    }
    
    return <Tag style={{ ...defaultStyle, ...(textObj.style || {}) }}>{textObj.text}</Tag>;
  };

  const getMediaStyle = (index: number, total: number, layout: string): React.CSSProperties => {
    const isContain = imageFit === 'contain';
    const height = isContain ? 'auto' : '100%';
    const alignSelf = isContain ? 'center' : 'stretch';

    if (layout === 'row' || layout === 'stack') {
      return { 
        flex: isContain ? '0 1 auto' : 1, 
        width: isContain ? 'auto' : '100%', 
        height, 
        maxWidth: '100%',
        maxHeight: '100%',
        objectFit: imageFit, 
        borderRadius: '16px', 
        boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)', 
        minWidth: 0, 
        minHeight: 0, 
        alignSelf 
      };
    }
    if (layout === 'grid') {
      return { 
        width: isContain ? 'auto' : '100%', 
        height: isContain ? 'auto' : '100%', 
        maxWidth: '100%',
        maxHeight: isContain ? '35vh' : '100%',
        objectFit: imageFit, 
        borderRadius: '16px', 
        boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.1)', 
        minWidth: 0, 
        minHeight: 0, 
        alignSelf 
      };
    }
    // Default collage
    if (total === 1) {
      return { width: '100%', height: '100%', objectFit: imageFit, borderRadius: '16px', boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)' };
    }
    if (total === 2) {
      if (index === 0) {
        return { position: 'absolute', left: 0, top: '5%', width: '70%', height: '90%', objectFit: imageFit, borderRadius: '16px', zIndex: 1, boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)' };
      } else {
        return { position: 'absolute', right: 0, bottom: '5%', width: '70%', height: '90%', objectFit: imageFit, borderRadius: '16px', zIndex: 2, boxShadow: '-10px 20px 25px -5px rgba(0, 0, 0, 0.15)', transform: 'translateY(-5%)' };
      }
    }
    // 3 images - diagonal cascade
    if (index === 0) {
      return { position: 'absolute', left: '5%', top: '5%', width: '60%', height: '70%', objectFit: imageFit, borderRadius: '16px', zIndex: 1, boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)' };
    } else if (index === 1) {
      return { position: 'absolute', left: '20%', top: '15%', width: '60%', height: '70%', objectFit: imageFit, borderRadius: '16px', zIndex: 2, boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)' };
    } else {
      return { position: 'absolute', right: '5%', bottom: '5%', width: '60%', height: '70%', objectFit: imageFit, borderRadius: '16px', zIndex: 3, boxShadow: '-10px 25px 50px -12px rgba(0, 0, 0, 0.25)' };
    }
  };

  const getContainerStyle = (layout: string, total: number): React.CSSProperties => {
    const isContain = imageFit === 'contain';
    const base: React.CSSProperties = { flex: 1, marginTop: '3rem', width: '100%', minHeight: '300px' };
    if (layout === 'row') return { ...base, display: 'flex', flexDirection: 'row', gap: '2rem', justifyContent: isContain ? 'center' : 'flex-start' };
    if (layout === 'stack') return { ...base, display: 'flex', flexDirection: 'column', gap: '2rem', justifyContent: isContain ? 'center' : 'flex-start' };
    if (layout === 'grid') {
      return { 
        ...base, 
        display: 'grid', 
        gridTemplateColumns: total === 1 ? '1fr' : (total === 2 ? '1fr 1fr' : '1fr 1fr'), 
        gridTemplateRows: 'auto',
        justifyItems: isContain ? 'center' : 'stretch',
        gap: '2rem' 
      };
    }
    return { ...base, position: 'relative' }; // collage
  };

  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      padding: 'clamp(2rem, 6cqmin, 4rem)',
      boxSizing: 'border-box',
      background: background || 'linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%)', // subtle gradient background
      position: 'relative',
      overflow: 'hidden'
    }}>
      
      {/* Top Text Heavy Section */}
      <div style={{ zIndex: 10, flexShrink: 0 }}>
        {renderText(eyebrow, {
          display: 'inline-block',
          padding: '0.5rem 1rem',
          background: '#3b82f615',
          color: '#2563eb',
          borderRadius: '9999px',
          fontSize: '0.875rem',
          fontWeight: 700,
          textTransform: 'uppercase',
          letterSpacing: '0.05em',
          marginBottom: '1.5rem'
        }, 'div')}
        
        {renderText(title, {
          fontSize: 'clamp(2.5rem, 8cqmin, 4rem)',
          fontWeight: 900,
          color: '#0f172a',
          margin: '0 0 1.5rem 0',
          lineHeight: 1.1,
          letterSpacing: '-0.02em',
          maxWidth: '90%'
        }, 'h1')}
        
        {renderText(subtitle, {
          fontSize: 'clamp(1.25rem, 3cqmin, 1.5rem)',
          color: '#475569',
          margin: 0,
          lineHeight: 1.5,
          maxWidth: '85%'
        }, 'p')}
      </div>

      {/* Bottom Media Collage Section */}
      {mediaUrls && mediaUrls.length > 0 && (
        <div style={getContainerStyle(mediaLayout, Math.min(mediaUrls.length, 3))}>
          {mediaUrls.slice(0, 3).map((url, idx) => {
            const isThirdGridItem = mediaLayout === 'grid' && Math.min(mediaUrls.length, 3) === 3 && idx === 2;
            const extraStyle = isThirdGridItem ? { gridColumn: '1 / span 2' } : {};
            return (
              <img 
                key={idx}
                src={url}
                alt={`Collage item ${idx + 1}`}
                style={{
                  ...getMediaStyle(idx, Math.min(mediaUrls.length, 3), mediaLayout),
                  ...extraStyle,
                  border: '1px solid rgba(255, 255, 255, 0.4)', // Glassmorphism edge
                }}
              />
            );
          })}
        </div>
      )}
    </div>
  );
};
