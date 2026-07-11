import React from 'react';
import type { ListSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';
import { renderText } from '../utils';

interface Props {
  content: ListSlideContent;
}

export const ListSlide: React.FC<Props> = ({ content }) => {
  return (
    <div style={{
      display: 'flex',
      flexDirection: 'column',
      gap: content.gap || '1cqi',
      boxSizing: 'border-box',
      ...(content.containerStyle || {})
    }}>
      {content.items.map((item, idx) => (
        <div key={idx} style={{
          display: 'flex',
          alignItems: 'flex-start',
          gap: '1cqi'
        }}>
          {item.icon && (
            <div style={{ flexShrink: 0, marginTop: '0.2cqi' }}>
              <DynamicIcon name={item.icon} size="1.2em" color={item.iconColor || 'currentColor'} />
            </div>
          )}
          <div style={{ flex: 1, minWidth: 0 }}>
            {renderText(item.text, { fontSize: '1.4cqi', lineHeight: 1.4 }, 'div')}
          </div>
        </div>
      ))}
    </div>
  );
};
