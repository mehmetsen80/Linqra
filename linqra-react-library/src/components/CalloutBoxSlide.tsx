import React from 'react';
import type { CalloutBoxSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: CalloutBoxSlideContent;
}
export const CalloutBoxSlide: React.FC<Props> = ({ content }) => {
  const containerId = React.useId().replace(/:/g, '');

  const renderText = (textObj: any, defaultStyle: React.CSSProperties = {}, Tag: any = 'div') => {
    if (!textObj) return null;
    if (typeof textObj === 'string') {
      return <Tag style={defaultStyle}>{textObj}</Tag>;
    }
    return (
      <Tag style={{ ...defaultStyle, ...(textObj.style || {}) }}>
        {textObj.icon && <span style={{ marginRight: '0.5rem', flexShrink: 0, display: 'inline-flex', verticalAlign: 'middle' }}><DynamicIcon name={textObj.icon} size={24} color={textObj.iconColor || 'currentColor'} /></span>}
        {textObj.text}
      </Tag>
    );
  };

  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'center',
      padding: 'clamp(1rem, 5cqi, 3rem)',
      boxSizing: 'border-box',
      containerType: 'inline-size',
      gap: '2rem'
    }}>
      {content.callouts?.map((callout, idx) => {
        const {
          text,
          icon,
          iconColor = 'currentColor',
          iconSize = 32,
          iconPosition = 'left',
          boxBg = '#2563eb', // Default blue from user's image
          boxTextColor = '#ffffff', // Default white text
          boxBorderRadius = '12px',
          boxPadding = 'clamp(1rem, 3cqmin, 2rem) clamp(1.5rem, 4cqmin, 3rem)',
          boxShadow = '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)'
        } = callout;

        const isVertical = iconPosition === 'top' || iconPosition === 'bottom';
        const flexDirection = 
          iconPosition === 'left' ? 'row' : 
          iconPosition === 'right' ? 'row-reverse' : 
          iconPosition === 'top' ? 'column' : 'column-reverse';

        return (
          <div 
            key={idx}
            className={`callout-box-${containerId}-${idx}`}
            style={{
              background: boxBg,
              borderRadius: boxBorderRadius,
              padding: boxPadding,
              boxShadow: boxShadow,
              display: 'flex',
              flexDirection: flexDirection,
              alignItems: isVertical ? 'flex-start' : 'center',
              gap: 'clamp(1rem, 3cqmin, 2rem)',
              maxWidth: '100%',
              width: '100%'
            }}
          >
            <style>{`
              .callout-box-${containerId}-${idx} {
                color: ${boxTextColor};
              }
              @container (max-width: 600px) {
                .callout-box-${containerId}-${idx} {
                  flex-direction: column !important;
                  align-items: flex-start !important;
                }
              }
            `}</style>

            {icon && (
              <div style={{ flexShrink: 0, display: 'flex' }}>
                <DynamicIcon name={icon} size={iconSize} color={iconColor} />
              </div>
            )}

            {text && (
              <div style={{ 
                flex: 1, 
                display: 'flex', 
                alignItems: 'center',
                fontSize: 'clamp(1rem, 4cqi, 1.25rem)',
                fontWeight: 500,
                lineHeight: 1.5,
                wordBreak: 'break-word'
              }}>
                {renderText(text)}
              </div>
            )}
          </div>
        );
      })}
    </div>
  );
};
