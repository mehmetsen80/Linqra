import React from 'react';
import type { CheckboxSlideContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: CheckboxSlideContent;
}

export const CheckboxSlide: React.FC<Props> = ({ content }) => {
  const getVariantStyles = () => {
    switch (content.variant) {
      case 'success':
        return { bg: '#22c55e', border: '#22c55e', check: '#ffffff' };
      case 'warning':
        return { bg: '#f59e0b', border: '#f59e0b', check: '#ffffff' };
      case 'error':
        return { bg: '#ef4444', border: '#ef4444', check: '#ffffff' };
      case 'neutral':
        return { bg: '#64748b', border: '#64748b', check: '#ffffff' };
      case 'primary':
      default:
        return { bg: '#2563eb', border: '#2563eb', check: '#ffffff' };
    }
  };

  const checkedState = content.checked ?? false;
  
  const styles = getVariantStyles();
  const size = content.size || '1.5cqi';

  return (
    <div style={{
      display: 'inline-flex',
      alignItems: 'center',
      gap: '0.8cqi',
      ...(content.containerStyle || {})
    }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        width: size,
        height: size,
        flexShrink: 0,
        border: `2px solid ${checkedState ? styles.border : '#cbd5e1'}`,
        backgroundColor: checkedState ? styles.bg : 'transparent',
        borderRadius: '4px',
        transition: 'all 0.2s ease',
      }}>
        {checkedState && (
          <DynamicIcon name="Check" size="80%" color={styles.check} />
        )}
      </div>
      {content.label && (
        <div style={{ userSelect: 'none', display: 'flex', alignItems: 'center' }}>
          {renderText(content.label, { fontSize: '1.2cqi', color: '#334155' }, 'span')}
        </div>
      )}
    </div>
  );
};
