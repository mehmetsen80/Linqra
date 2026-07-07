import React from 'react';
import type { FeatureBannerSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: FeatureBannerSlideContent;
}

export const FeatureBannerSlide: React.FC<Props> = ({ content }) => {
  const { 
    icon, 
    iconBg = '#eef2ff', 
    iconColor = '#4f46e5', 
    iconPosition = 'left', 
    iconAlignment = 'center', 
    cardBg = '#f8fafc',
    cardBorderColor = '#e2e8f0',
    dividerStyle,
    title, 
    features 
  } = content;

  const renderText = (textObj: any, defaultStyle: React.CSSProperties = {}, Tag: any = 'span') => {
    if (!textObj) return null;
    if (typeof textObj === 'string') return <Tag style={defaultStyle}>{textObj}</Tag>;
    return <Tag style={{ ...defaultStyle, ...(textObj.style || {}) }}>{textObj.text}</Tag>;
  };

  const getAlignment = () => {
    switch (iconAlignment) {
      case 'top': return 'flex-start';
      case 'bottom': return 'flex-end';
      case 'center':
      default: return 'center';
    }
  };

  const iconSection = icon ? (
    <div style={{
      display: 'flex',
      alignItems: getAlignment(),
      justifyContent: 'center',
      padding: iconPosition === 'left' ? '0 clamp(1.5rem, 3cqmin, 3rem) 0 0' : '0 0 0 clamp(1.5rem, 3cqmin, 3rem)',
      flexShrink: 0
    }}>
      <div style={{
        width: 'clamp(80px, 12cqmin, 120px)',
        height: 'clamp(80px, 12cqmin, 120px)',
        borderRadius: '50%',
        backgroundColor: iconBg,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)'
      }}>
        <DynamicIcon name={icon} size={64} color={iconColor} />
      </div>
    </div>
  ) : null;

  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'center',
      justifyContent: 'flex-start',
      padding: 'clamp(1rem, 3cqmin, 3rem)',
      boxSizing: 'border-box',
      containerType: 'inline-size'
    }}>
      {/* Outer Card Container */}
      <div style={{
        width: '100%',
        background: cardBg,
        border: `1px solid ${cardBorderColor}`,
        borderRadius: '24px',
        padding: 'clamp(1.5rem, 4cqmin, 3rem)',
        display: 'flex',
        flexDirection: iconPosition === 'left' ? 'row' : 'row-reverse',
        boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.05)'
      }}>
        
        {iconSection}

        <div style={{ flex: 1, display: 'flex', flexDirection: 'column' }}>
          
          {/* Section Title */}
          {title && (
            <div style={{ marginBottom: '2rem' }}>
              {renderText(title, {
                fontSize: 'clamp(1.5rem, 3cqmin, 2rem)',
                fontWeight: 800,
                color: '#0f172a',
                margin: 0
              }, 'h2')}
            </div>
          )}

          {/* Features Grid */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: `repeat(${features.length}, 1fr)`,
            gap: '0' // gap is 0 so borders can touch
          }}>
            {features.map((feature, idx) => {
              const isLast = idx === features.length - 1;
              const fIconColor = feature.iconColor || '#2563eb';
              
              return (
                <div key={idx} style={{
                  display: 'flex',
                  flexDirection: 'column',
                  paddingRight: isLast ? '0' : 'clamp(1rem, 2cqmin, 2rem)',
                  paddingLeft: idx === 0 ? '0' : 'clamp(1rem, 2cqmin, 2rem)',
                  borderRight: isLast ? 'none' : `${dividerStyle?.thickness || '1px'} ${dividerStyle?.style || 'solid'} ${dividerStyle?.color || '#e2e8f0'}`
                }}>
                  
                  {/* Feature Header: Icon + Title */}
                  <div style={{
                    display: 'flex',
                    alignItems: 'flex-start',
                    gap: '0.75rem',
                    marginBottom: '1rem'
                  }}>
                    <div style={{
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      flexShrink: 0,
                      marginTop: '2px'
                    }}>
                      <DynamicIcon name={feature.icon || 'CheckCircle2'} size={24} color={fIconColor} />
                    </div>
                    {renderText(feature.title, {
                      fontSize: 'clamp(1rem, 1.3cqmin, 1.25rem)',
                      fontWeight: 700,
                      color: fIconColor,
                      lineHeight: 1.3,
                      margin: 0
                    }, 'h3')}
                  </div>

                  {/* Feature Description */}
                  <div>
                    {renderText(feature.description, {
                      fontSize: 'clamp(0.85rem, 1.1cqmin, 1.05rem)',
                      lineHeight: 1.6,
                      color: '#475569',
                      margin: 0
                    }, 'p')}
                  </div>

                </div>
              );
            })}
          </div>

        </div>
      </div>
    </div>
  );
};
