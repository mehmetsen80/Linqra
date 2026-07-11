import React from 'react';
import type { ButtonSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';
import { renderText } from '../utils';

interface Props {
  content: ButtonSlideContent;
}

export const ButtonSlide: React.FC<Props> = ({ content }) => {
  const getVariantStyles = () => {
    switch (content.variant) {
      case 'secondary':
        return { background: '#f1f5f9', color: '#0f172a', border: '1px solid #e2e8f0' };
      case 'outline':
        return { background: 'transparent', color: 'var(--text-main, #0f172a)', border: '2px solid currentColor' };
      case 'ghost':
        return { background: 'transparent', color: 'var(--text-main, #4f46e5)', border: '1px solid transparent' };
      case 'primary':
      default:
        return { background: '#2563eb', color: '#ffffff', border: '1px solid #2563eb' };
    }
  };

  return (
    <div style={{
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      gap: '1cqi',
      padding: '1cqi 2.5cqi',
      borderRadius: '8px',
      fontSize: '1.4cqi',
      fontWeight: 600,
      cursor: 'pointer',
      boxSizing: 'border-box',
      whiteSpace: 'nowrap',
      ...getVariantStyles(),
      ...(content.containerStyle || {})
    }}>
      {content.icon && <DynamicIcon name={content.icon} size="1.2em" />}
      {renderText(content.text, {}, 'span')}
    </div>
  );
};
