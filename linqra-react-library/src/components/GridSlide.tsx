import React from 'react';
import type { GridSlideContent } from '../schemas';

import { SlideComponentRenderer } from './SlideComponentRenderer';

interface Props {
  content: GridSlideContent;
  children?: React.ReactNode;
}

export const GridSlide: React.FC<Props> = ({ content, children }) => {
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: content.columns || 'repeat(auto-fit, minmax(200px, 1fr))',
      gap: content.gap || '2cqi',
      boxSizing: 'border-box',
      width: '100%',
      ...(content.containerStyle || {})
    }}>
      {content.children?.map((child, idx) => (
        <SlideComponentRenderer key={`child-${idx}`} slide={child} />
      ))}
      {children}
    </div>
  );
};
