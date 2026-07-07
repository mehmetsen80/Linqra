import React from 'react';
import type { FooterData } from '../schemas';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: FooterData;
}

export const PresentationFooter: React.FC<Props> = ({ content }) => {
  const {
    icon,
    iconColor = '#4f46e5',
    iconBg = 'transparent',
    textLeft,
    textRight,
    dividerStyle,
    containerStyle = {},
    contentStyle = {}
  } = content;

  const renderText = (textObj: any, defaultStyle: React.CSSProperties = {}, Tag: any = 'span') => {
    if (!textObj) return null;
    if (typeof textObj === 'string') {
      return <Tag style={defaultStyle}>{textObj}</Tag>;
    }
    return <Tag style={{ ...defaultStyle, ...textObj.style }}>{textObj.text}</Tag>;
  };

  return (
    <div style={{
      width: '100%',
      padding: 'clamp(0.5rem, 1.5cqmin, 1.5rem) clamp(2rem, 4cqmin, 4rem) 0.5rem clamp(2rem, 4cqmin, 4rem)',
      display: 'flex',
      alignItems: 'center',
      boxSizing: 'border-box',
      background: 'transparent',
      containerType: 'inline-size',
      zIndex: 10,
      ...containerStyle
    }}>
      <div style={{
        display: 'flex',
        alignItems: 'center',
        gap: '0.75rem',
        ...contentStyle
      }}>
        {icon && (
          <div style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            background: iconBg,
            borderRadius: '50%',
            padding: iconBg !== 'transparent' ? '0.5rem' : '0'
          }}>
            <DynamicIcon name={icon} size={28} color={iconColor} />
          </div>
        )}

        {textLeft && renderText(textLeft, {
          fontSize: 'clamp(0.875rem, 1.5cqmin, 1.25rem)',
          fontWeight: 700,
          color: '#1e293b',
          lineHeight: 1,
          letterSpacing: '-0.01em'
        })}

        {textLeft && textRight && (
          <div style={{
            height: 'clamp(1rem, 2cqmin, 1.5rem)',
            borderLeft: `${dividerStyle?.thickness || '1px'} ${dividerStyle?.style || 'solid'} ${dividerStyle?.color || '#cbd5e1'}`,
            margin: '0 0.25rem'
          }} />
        )}

        {textRight && renderText(textRight, {
          fontSize: 'clamp(0.875rem, 1.5cqmin, 1.25rem)',
          fontWeight: 400,
          color: '#64748b',
          lineHeight: 1
        })}
      </div>
    </div>
  );
};
