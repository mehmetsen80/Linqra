import React from 'react';
import type { FlexSlideContent } from '../schemas';

import { SlideComponentRenderer } from './SlideComponentRenderer';

interface Props {
  content: FlexSlideContent;
  children?: React.ReactNode;
}

export const FlexSlide: React.FC<Props> = ({ content, children }) => {
  return (
    <div style={{
      display: 'flex',
      flexDirection: content.direction || 'row',
      alignItems: content.align || 'stretch',
      justifyContent: content.justify || 'flex-start',
      gap: content.gap || '0',
      flexWrap: content.wrap ? 'wrap' : 'nowrap',
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
