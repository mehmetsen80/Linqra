import React from 'react';
import type { PricingTierSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: PricingTierSlideContent;
}

export const PricingTierSlide: React.FC<Props> = ({ content }) => {
  const { title, subtitle, tiers = [] } = content;

  const renderText = (textObj: any, defaultStyle: React.CSSProperties = {}, Tag: any = 'span') => {
    if (!textObj) return null;
    if (typeof textObj === 'string') {
      return <Tag style={defaultStyle}>{textObj}</Tag>;
    }
    return <Tag style={{ ...defaultStyle, ...(textObj.style || {}) }}>{textObj.text}</Tag>;
  };

  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      padding: 'clamp(1rem, 3cqmin, 2rem) clamp(1.5rem, 4cqmin, 3rem)',
      boxSizing: 'border-box',
      containerType: 'inline-size'
    }}>
      {/* Title Section */}
      {(title || subtitle) && (
        <div style={{ textAlign: 'center', marginBottom: 'clamp(1.5rem, 4cqmin, 3rem)' }}>
          {title && renderText(title, { fontSize: 'clamp(2rem, 6cqmin, 3rem)', fontWeight: 800, color: '#0f172a', margin: 0 }, 'h2')}
          {subtitle && renderText(subtitle, { fontSize: 'clamp(1rem, 2.5cqmin, 1.25rem)', color: '#64748b', marginTop: '0.5rem' }, 'p')}
        </div>
      )}

      {/* Tiers Grid */}
      <div style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'row',
        justifyContent: 'center',
        alignItems: 'stretch',
        gap: 'clamp(1rem, 2cqmin, 2rem)',
        overflowY: 'visible',
        padding: '1rem',
        margin: '-1rem'
      }}>
        {tiers.map((tier, idx) => {
          const isHighlighted = tier.isHighlighted;
          const cardBg = tier.cardStyle?.background || (isHighlighted ? '#ffffff' : '#f8fafc');
          const cardBorder = tier.cardStyle?.borderColor || (isHighlighted ? '#3b82f6' : '#e2e8f0');
          
          return (
            <div key={idx} style={{
              flex: '1 1 0',
              minWidth: 0,
              maxWidth: '380px',
              display: 'flex',
              flexDirection: 'column',
              background: cardBg,
              border: `2px solid ${cardBorder}`,
              borderRadius: '16px',
              padding: 'clamp(0.75rem, 2cqmin, 2rem)',
              position: 'relative',
              boxShadow: isHighlighted ? '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)' : 'none',
              transform: isHighlighted ? 'scale(1.02)' : 'none',
              transition: 'transform 0.2s',
              boxSizing: 'border-box'
            }}>
              {/* Highlight Badge */}
              {isHighlighted && tier.highlightText && (
                <div style={{
                  position: 'absolute',
                  top: '-12px',
                  left: '50%',
                  transform: 'translateX(-50%)',
                  background: '#3b82f6',
                  color: '#ffffff',
                  padding: '4px 12px',
                  borderRadius: '9999px',
                  fontSize: 'clamp(0.6rem, 1.5cqmin, 0.75rem)',
                  fontWeight: 700,
                  letterSpacing: '0.05em',
                  textTransform: 'uppercase',
                  whiteSpace: 'nowrap'
                }}>
                  {renderText(tier.highlightText, {}, 'span')}
                </div>
              )}

              {/* Tier Header */}
              <div style={{ marginBottom: 'clamp(0.5rem, 2cqmin, 1.5rem)', textAlign: 'center' }}>
                {renderText(tier.name, { fontSize: 'clamp(1rem, 3cqmin, 1.25rem)', fontWeight: 600, color: isHighlighted ? '#3b82f6' : '#475569', marginBottom: '0.5rem' }, 'h3')}
                <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'center', gap: '0.25rem' }}>
                  {renderText(tier.price, { fontSize: 'clamp(1.5rem, 6cqmin, 2.5rem)', fontWeight: 800, color: '#0f172a', margin: 0 }, 'h4')}
                  {tier.period && renderText(tier.period, { fontSize: 'clamp(0.8rem, 2.5cqmin, 1rem)', color: '#64748b' }, 'span')}
                </div>
                {tier.description && renderText(tier.description, { fontSize: 'clamp(0.7rem, 2cqmin, 0.875rem)', color: '#64748b', marginTop: '0.5rem', display: 'block' }, 'p')}
              </div>

              {/* Features List */}
              <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 'clamp(0.25rem, 1cqmin, 1rem)', marginBottom: 'clamp(0.5rem, 3cqmin, 2rem)' }}>
                {tier.features.map((feature, fIdx) => {
                  const isExcluded = feature.isExcluded;
                  const defaultIcon = isExcluded ? 'X' : 'Check';
                  const defaultIconColor = isExcluded ? '#94a3b8' : '#10b981';
                  
                  const icon = feature.icon || defaultIcon;
                  const iconColor = feature.iconColor || defaultIconColor;
                  const iconBg = feature.iconBg || 'transparent';
                  
                  return (
                    <div key={fIdx} style={{ display: 'flex', alignItems: 'flex-start', gap: 'clamp(0.25rem, 1cqmin, 0.75rem)' }}>
                      <div style={{
                        display: 'flex', alignItems: 'center', justifyContent: 'center',
                        width: 'clamp(18px, 3cqmin, 24px)', height: 'clamp(18px, 3cqmin, 24px)',
                        borderRadius: '50%', background: iconBg, flexShrink: 0,
                        marginTop: '2px'
                      }}>
                        <DynamicIcon name={icon} size={18} color={iconColor} />
                      </div>
                      {renderText(feature.text, {
                        fontSize: 'clamp(0.75rem, 2.2cqmin, 0.95rem)',
                        color: isExcluded ? '#94a3b8' : '#334155',
                        textDecoration: isExcluded ? 'line-through' : 'none',
                        lineHeight: 1.4
                      }, 'span')}
                    </div>
                  );
                })}
              </div>

              {/* CTA Button */}
              {tier.buttonText && (
                <div style={{
                  width: '100%',
                  padding: 'clamp(0.5rem, 1.5cqmin, 0.75rem)',
                  borderRadius: '8px',
                  background: tier.buttonStyle?.background || (isHighlighted ? '#3b82f6' : '#f1f5f9'),
                  color: tier.buttonStyle?.color || (isHighlighted ? '#ffffff' : '#0f172a'),
                  border: tier.buttonStyle?.border || 'none',
                  textAlign: 'center',
                  fontWeight: 600,
                  fontSize: '1rem',
                  cursor: 'pointer'
                }}>
                  {renderText(tier.buttonText, {}, 'span')}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};
