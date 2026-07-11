import React from 'react';
import type { ImageSlideContent } from '../schemas';

interface Props {
  content: ImageSlideContent;
}

export const ImageSlide: React.FC<Props> = ({ content }) => {
  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      overflow: 'hidden',
      ...(content.containerStyle || {})
    }}>
      <img 
        src={content.src} 
        alt={content.alt || ''} 
        style={{
          width: '100%',
          height: '100%',
          objectFit: content.objectFit || 'cover'
        }}
      />
    </div>
  );
};
