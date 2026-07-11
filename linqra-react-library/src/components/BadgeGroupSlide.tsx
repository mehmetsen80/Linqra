import React from 'react';
import type { BadgeGroupSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';
import { renderText } from '../utils';

interface Props {
  content: BadgeGroupSlideContent;
}

export const BadgeGroupSlide: React.FC<Props> = ({ content }) => {
  const alignMap = {
    'left': 'flex-start',
    'center': 'center',
    'right': 'flex-end',
    'space-between': 'space-between'
  };

  const justifyContent = alignMap[content.alignment || 'left'];
  const flexDirection = content.layout === 'column' ? 'column' : 'row';

  return (
    <div style={{
      width: '100%',
      display: 'flex',
      flexDirection,
      justifyContent,
      alignItems: flexDirection === 'column' ? justifyContent : 'center',
      gap: content.gap || '1cqi',
      flexWrap: 'wrap',
      boxSizing: 'border-box',
      ...(content.containerStyle || {})
    }}>
      {content.badges.map((badge, idx) => {
        const customStyle = typeof badge === 'string' ? {} : (badge.style || {});
        const badgeText = typeof badge === 'string' ? badge : (badge.text || '');
        const icon = typeof badge === 'string' ? undefined : badge.icon;
        const iconSize = typeof badge === 'string' ? undefined : badge.iconSize;
        return (
          <div key={`badge-${idx}`} style={{
            padding: '1cqi 2cqi',
            borderRadius: '9999px',
            background: 'var(--badge-bg, rgba(0, 0, 0, 0.05))',
            color: 'var(--badge-text, var(--text-main))',
            fontWeight: '600',
            fontSize: '1.4cqi',
            border: '1px solid var(--badge-border, transparent)',
            display: 'flex',
            alignItems: 'center',
            gap: '0.5cqi',
            ...customStyle
          }}>
            {icon && <DynamicIcon name={icon} size={iconSize || "1.2em"} />}
            {renderText(badgeText, { margin: 0 }, 'span')}
          </div>
        );
      })}
    </div>
  );
};
