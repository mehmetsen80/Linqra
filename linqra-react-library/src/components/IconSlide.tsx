import React from 'react';
import type { IconSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: IconSlideContent;
}

export const IconSlide: React.FC<Props> = ({ content }) => {
  return (
    <div style={{
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      ...(content.containerStyle || {})
    }}>
      <DynamicIcon name={content.name} size={content.size || "1em"} color={content.color || 'currentColor'} />
    </div>
  );
};
