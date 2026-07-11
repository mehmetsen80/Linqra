import React from 'react';
import type { AvatarSlideContent, TextContent } from '../schemas';

interface Props {
  content: AvatarSlideContent;
}

export const AvatarSlide: React.FC<Props> = ({ content }) => {
  const size = content.size || '3cqi';
  
  const getInitials = (nameInput?: TextContent) => {
    if (!nameInput) return '?';
    const nameStr = typeof nameInput === 'object' ? nameInput.text : nameInput;
    if (!nameStr) return '?';
    
    const parts = nameStr.split(' ');
    if (parts.length >= 2) {
      return `${parts[0][0]}${parts[1][0]}`.toUpperCase();
    }
    return nameStr.substring(0, 2).toUpperCase();
  };

  return (
    <div style={{
      width: size,
      height: size,
      borderRadius: '50%',
      overflow: 'hidden',
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      flexShrink: 0,
      background: content.fallbackColor || '#e2e8f0',
      color: content.fallbackTextColor || '#64748b',
      fontSize: `calc(${typeof size === 'string' ? size : `${size}px`} * 0.4)`,
      fontWeight: '600',
      border: content.border || 'none',
      ...(content.containerStyle || {})
    }}>
      {content.src ? (
        <img 
          src={content.src} 
          alt={content.alt || (typeof content.name === 'object' ? content.name.text : content.name) || 'Avatar'} 
          style={{
            width: '100%',
            height: '100%',
            objectFit: 'cover'
          }}
        />
      ) : (
        <span>{getInitials(content.name)}</span>
      )}
    </div>
  );
};
