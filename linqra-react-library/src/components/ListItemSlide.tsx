import React from 'react';
import type { ListItemSlideContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: ListItemSlideContent;
}

export const ListItemSlide: React.FC<Props> = ({ content }) => {
  const getVariantStyles = () => {
    switch (content.variant) {
      case 'success':
        return { iconBg: '#dcfce7', iconColor: '#16a34a' };
      case 'warning':
        return { iconBg: '#fef3c7', iconColor: '#d97706' };
      case 'error':
        return { iconBg: '#fee2e2', iconColor: '#dc2626' };
      case 'neutral':
        return { iconBg: '#f1f5f9', iconColor: '#64748b' };
      case 'primary':
      default:
        return { iconBg: '#eff6ff', iconColor: '#2563eb' };
    }
  };

  const styles = getVariantStyles();
  const iconColor = content.iconColor || styles.iconColor;
  
  // If a specific variant is passed but no iconColor, we use the variant's colors.
  // If no icon is passed, it just aligns text without icon.

  return (
    <div style={{
      display: 'flex',
      alignItems: 'flex-start',
      gap: '1.2cqi',
      ...(content.containerStyle || {})
    }}>
      {content.icon && (
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          width: '3cqi',
          height: '3cqi',
          borderRadius: '50%',
          backgroundColor: content.iconColor ? 'transparent' : styles.iconBg,
          flexShrink: 0
        }}>
          <DynamicIcon name={content.icon} size="1.5cqi" color={iconColor} />
        </div>
      )}
      
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.4cqi', paddingTop: content.icon ? '0.4cqi' : '0' }}>
        {renderText(content.title, { fontSize: '1.2cqi', fontWeight: 600, color: '#0f172a' }, 'div')}
        {content.description && renderText(content.description, { fontSize: '1cqi', color: '#64748b', lineHeight: 1.5 }, 'div')}
      </div>
    </div>
  );
};
