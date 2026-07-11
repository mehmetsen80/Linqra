import React from 'react';
import type { DividerSlideContent } from '../schemas';

interface Props {
  content: DividerSlideContent;
}

export const DividerSlide: React.FC<Props> = ({ content }) => {
  const isVertical = content.orientation === 'vertical';
  const thickness = content.thickness || '1px';
  const color = content.color || '#e2e8f0';

  const baseStyle: React.CSSProperties = {
    flexShrink: 0,
    background: color,
    margin: content.margin || 0,
    padding: 0,
    border: 'none',
  };

  if (isVertical) {
    baseStyle.width = thickness;
    baseStyle.height = '100%';
  } else {
    baseStyle.width = '100%';
    baseStyle.height = thickness;
  }

  return <div style={baseStyle} />;
};
