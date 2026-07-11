import React from 'react';
import type { CardSlideContent } from '../schemas';

import { SlideComponentRenderer } from './SlideComponentRenderer';

interface Props {
  content: CardSlideContent;
  children?: React.ReactNode;
}

export const CardSlide: React.FC<Props> = ({ content, children }) => {
  return (
    <div style={{
      backgroundColor: 'white',
      borderRadius: '8px',
      border: '1px solid #e2e8f0',
      padding: '2cqi',
      boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
      display: 'flex',
      flexDirection: 'column',
      boxSizing: 'border-box',
      ...(content.containerStyle || {})
    }}>
      {content.children?.map((child, idx) => (
        <SlideComponentRenderer key={`child-${idx}`} slide={child} />
      ))}
      {children}
    </div>
  );
};
