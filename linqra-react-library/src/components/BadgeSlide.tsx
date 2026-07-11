import React from 'react';
import type { BadgeSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';
import { renderText } from '../utils';

interface Props {
  content: BadgeSlideContent;
}

export const BadgeSlide: React.FC<Props> = ({ content }) => {
  const getVariantStyles = () => {
    switch (content.variant) {
      case 'success':
        return { background: '#f0fdf4', color: '#16a34a' };
      case 'warning':
        return { background: '#fffbeb', color: '#d97706' };
      case 'error':
        return { background: '#fef2f2', color: '#dc2626' };
      case 'info':
        return { background: '#eff6ff', color: '#2563eb' };
      case 'primary':
        return { background: '#ea580c', color: '#ffffff' };
      case 'neutral':
      default:
        return { background: '#f1f5f9', color: '#475569' };
    }
  };

  const variantStyles = getVariantStyles();

  return (
    <div style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: '0.4cqi',
      padding: '0.3cqi 0.8cqi',
      borderRadius: '4px',
      fontSize: '0.75cqi',
      fontWeight: '700',
      width: 'fit-content',
      ...variantStyles,
      ...(content.containerStyle || {})
    }}>
      {content.icon && (
        <DynamicIcon 
          name={content.icon} 
          size="1em" 
          color={content.containerStyle?.color || variantStyles.color as string} 
        />
      )}
      {renderText(content.text, content.textStyle || {}, 'span')}
    </div>
  );
};
