import React from 'react';
import type { ConnectorSlideContent } from '../schemas';

interface Props {
  content: ConnectorSlideContent;
}

export const ConnectorSlide: React.FC<Props> = ({ content }) => {
  const isVertical = content.direction === 'vertical';
  
  return (
    <div style={{
      ...(isVertical ? {
        width: '2px',
        height: content.length || '100%',
      } : {
        height: '2px',
        width: content.length || '100%',
      }),
      borderStyle: content.style || 'solid',
      borderWidth: isVertical ? '0 0 0 2px' : '2px 0 0 0',
      borderColor: content.color || '#cbd5e1',
      flexShrink: 0
    }} />
  );
};
