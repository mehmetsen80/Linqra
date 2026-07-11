import React from 'react';
import type { BrandLogoSlideContent } from '../schemas';

interface Props {
  content: BrandLogoSlideContent;
}

export const BrandLogoSlide: React.FC<Props> = ({ content }) => {
  const img = content.image;
  const imgUrl = typeof img === 'string' ? img : img.url;
  const imgAlt = typeof img === 'string' ? 'Logo' : (img.alt || 'Logo');
  const imgStyle = typeof img === 'string' ? {} : (img.style || {});

  return (
    <div style={{
      containerType: 'inline-size',
      width: '100%',
      height: '100%',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      boxSizing: 'border-box',
      ...(content.containerStyle || {})
    }}>
      <img 
        src={imgUrl} 
        alt={imgAlt} 
        style={{
          width: '100%',
          height: '100%',
          objectFit: 'contain',
          ...imgStyle
        }} 
      />
    </div>
  );
};
