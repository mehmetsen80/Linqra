import React from 'react';
import type { TextBlockSlideContent } from '../schemas';
import { renderText } from '../utils';

interface Props {
  content: TextBlockSlideContent;
}

export const TextBlockSlide: React.FC<Props> = ({ content }) => {
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: content.gap || '0.5cqi',
      textAlign: content.alignment || 'left',
      boxSizing: 'border-box',
      ...(content.containerStyle || {})
    }}>
      {content.blocks.map((block, idx) => {
        return renderText(block, { margin: 0 }, 'div', idx);
      })}
    </div>
  );
};
