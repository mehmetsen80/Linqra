import React from 'react';
import type { AlertSlideContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: AlertSlideContent;
}

export const AlertSlide: React.FC<Props> = ({ content }) => {
  const getVariantStyles = () => {
    switch (content.variant) {
      case 'success':
        return { bg: '#f0fdf4', border: '#bbf7d0', text: '#166534', icon: '#22c55e', defaultIcon: 'CheckCircle2' };
      case 'warning':
        return { bg: '#fffbeb', border: '#fde68a', text: '#92400e', icon: '#f59e0b', defaultIcon: 'AlertTriangle' };
      case 'error':
        return { bg: '#fef2f2', border: '#fecaca', text: '#991b1b', icon: '#ef4444', defaultIcon: 'XCircle' };
      case 'info':
      default:
        return { bg: '#eff6ff', border: '#bfdbfe', text: '#1e40af', icon: '#3b82f6', defaultIcon: 'Info' };
    }
  };

  const styles = getVariantStyles();
  const iconName = content.icon || styles.defaultIcon;

  return (
    <div style={{
      display: 'flex',
      gap: '1cqi',
      padding: '1cqi 1.2cqi',
      background: styles.bg,
      border: `1px solid ${styles.border}`,
      borderRadius: '8px',
      color: styles.text,
      ...(content.containerStyle || {})
    }}>
      <div style={{ flexShrink: 0, marginTop: '0.1cqi' }}>
        <DynamicIcon name={iconName} size="1.2cqi" color={styles.icon} />
      </div>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.3cqi' }}>
        {content.title && renderText(content.title, { fontSize: '0.9cqi', fontWeight: 700 }, 'div')}
        {renderText(content.description, { fontSize: '0.8cqi', lineHeight: 1.5, opacity: 0.9 }, 'div')}
      </div>
    </div>
  );
};
