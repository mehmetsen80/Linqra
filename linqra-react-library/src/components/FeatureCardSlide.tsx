import React from 'react';
import type { FeatureCardSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';
import { renderText } from '../utils';

interface Props {
  content: FeatureCardSlideContent;
}

export const FeatureCardSlide: React.FC<Props> = ({ content }) => {
  return (
    <div style={{
      backgroundColor: '#ffffff',
      padding: '1.5cqi',
      border: '1px solid #cbd5e1',
      borderRadius: '8px',
      display: 'flex',
      flexDirection: 'column',
      gap: '1cqi',
      ...(content.containerStyle || {})
    }}>
      <div style={{ display: 'flex', gap: '1cqi', alignItems: 'center' }}>
        {content.icon && (
          <div style={{
            width: '3cqi',
            height: '3cqi',
            background: content.iconBg || '#fff7ed',
            border: `1px solid ${content.iconBorderColor || '#fed7aa'}`,
            borderRadius: '8px',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            flexShrink: 0
          }}>
            <DynamicIcon name={content.icon} color={content.iconColor || '#ea580c'} size="1.5cqi" />
          </div>
        )}
        <div>
          {renderText(content.title, { fontWeight: 800, fontSize: '1.1cqi', color: '#0f172a' }, 'div')}
          {content.subtitle && renderText(content.subtitle, { fontSize: '0.7cqi', fontWeight: 600, color: '#64748b', marginTop: '0.2cqi' }, 'div')}
        </div>
      </div>
      {renderText(content.description, { fontSize: '0.9cqi', color: '#475569', lineHeight: 1.5 }, 'div')}
    </div>
  );
};
