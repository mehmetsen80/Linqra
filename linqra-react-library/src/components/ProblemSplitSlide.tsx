import React from 'react';
import type { ProblemSplitSlideContent, TextContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: ProblemSplitSlideContent;
}

export const ProblemSplitSlide: React.FC<Props> = ({ content }) => {
  const renderBadge = (badge: TextContent, idx: number) => {
    const customStyle = typeof badge === 'string' ? {} : (badge.style || {});
    return (
      <div key={`badge-${idx}`} style={{
        padding: '0.2cqi 0.8cqi',
        borderRadius: '0.3cqi',
        background: 'var(--badge-bg, rgba(0, 0, 0, 0.05))',
        color: 'var(--badge-text, var(--text-main))',
        fontWeight: 'bold',
        fontSize: '0.9cqi',
        border: '1px solid var(--badge-border, rgba(0, 0, 0, 0.1))',
        ...customStyle
      }}>
        {renderText(badge, { margin: 0 }, 'span')}
      </div>
    );
  };

  return (
    <div style={{
      containerType: 'inline-size',
      padding: '3cqi',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      position: 'relative',
      overflow: 'hidden',
      boxSizing: 'border-box',
      background: 'var(--slide-bg)'
    }}>
      {/* Background Elements */}
      {content.backgroundElements?.map((styleObj, idx) => (
        <div key={`bg-elem-${idx}`} style={{ position: 'absolute', ...styleObj }}></div>
      ))}

      {/* Header */}
      <div style={{ position: 'relative', zIndex: 2, marginBottom: '1.5cqi' }}>
        {renderText(content.title, { fontSize: '2.8cqi', fontWeight: '800', margin: '0 0 0.5cqi 0', color: 'var(--title-color, var(--text-main))', letterSpacing: '-0.02cqi' }, 'h1')}
        {content.subtitle && renderText(content.subtitle, { fontSize: '1.3cqi', color: 'var(--text-secondary)', margin: 0 }, 'div')}
      </div>

      {/* Main Split Content */}
      <div style={{ display: 'flex', gap: content.columnGap || '3cqi', flex: 1, position: 'relative', zIndex: 2, minHeight: 0 }}>
        
        {/* Left Column - Cards */}
        <div style={{ flex: `0 0 ${content.leftColumnWidth || '45%'}`, display: 'flex', flexDirection: 'column', gap: content.leftGap || '1cqi', minHeight: 0 }}>
          {content.leftCards.map((card, idx) => (
            <div key={`lcard-${idx}`} style={{
              background: 'var(--card-bg, white)',
              borderRadius: '0.8cqi',
              border: '1px solid var(--card-border, #eaeaea)',
              padding: '1cqi 1.5cqi',
              position: 'relative',
              boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
              display: 'flex',
              flexDirection: 'column',
              justifyContent: 'center',
              ...(card.cardStyle || {})
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <div style={{ flex: 1 }}>
                  {renderText(card.category, { fontSize: '0.9cqi', fontWeight: '800', textTransform: 'uppercase', letterSpacing: '0.05cqi', marginBottom: '0.2cqi', color: 'var(--text-secondary)', ...(card.categoryStyle || {}) }, 'div')}
                  {renderText(card.value, { fontSize: '2.8cqi', fontWeight: '800', color: 'var(--text-main)', margin: 0, lineHeight: 1.1, ...(card.valueStyle || {}) }, 'div')}
                </div>
                {card.icon && (
                  <div style={{ color: card.iconColor || 'var(--text-tertiary)', width: '3cqi', height: '3cqi', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
                    <DynamicIcon name={card.icon} />
                  </div>
                )}
              </div>
              <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '1cqi', marginTop: '0.8cqi' }}>
                {renderText(card.description, { fontSize: '1cqi', color: 'var(--text-secondary)', margin: 0, lineHeight: 1.5, whiteSpace: 'pre-wrap' }, 'div')}
              </div>
              {card.badges && card.badges.length > 0 && (
                <div style={{ display: 'flex', gap: '0.5cqi', flexWrap: 'wrap', marginTop: '1.2cqi' }}>
                  {card.badges.map((b, i) => renderBadge(b, i))}
                </div>
              )}
            </div>
          ))}
        </div>

        {/* Right Column - Items */}
        <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: content.rightGap || '1cqi', justifyContent: 'space-between', minHeight: 0 }}>
          {content.rightItems.map((item, idx) => (
            <div key={`ritem-${idx}`} style={{
              background: 'var(--card-bg, white)',
              borderRadius: '0.8cqi',
              border: 'none',
              padding: '1.5cqi 2cqi',
              display: 'flex',
              gap: '1.5cqi',
              alignItems: 'flex-start',
              boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.02)',
              flex: 1,
              ...(item.cardStyle || {})
            }}>
              <div style={{
                flex: '0 0 4cqi',
                height: '4cqi',
                borderRadius: '0.8cqi',
                background: item.iconBg || '#f1f5f9',
                color: item.iconColor || 'var(--text-main)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                {item.icon && <DynamicIcon name={item.icon} />}
              </div>
              <div style={{ flex: 1 }}>
                {renderText(item.title, { fontSize: '1.2cqi', fontWeight: '700', color: 'var(--text-main)', marginBottom: '0.5cqi' }, 'div')}
                {renderText(item.description, { fontSize: '1cqi', color: 'var(--text-secondary)', lineHeight: 1.5, margin: 0 }, 'div')}
              </div>
            </div>
          ))}
        </div>

      </div>

      {/* Bottom Banner */}
      {content.bottomBanner && (
        <div style={{
          marginTop: 'auto',
          position: 'relative',
          zIndex: 2,
          background: 'var(--card-bg, white)',
          borderRadius: '0.8cqi',
          border: '1px solid var(--card-border, #eaeaea)',
          padding: '1.2cqi 2cqi',
          display: 'flex',
          gap: '2cqi',
          alignItems: 'center',
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
          ...(content.bottomBanner.bannerStyle || {})
        }}>
          {content.bottomBanner.icon && (
            <div style={{
              flex: '0 0 4cqi',
              height: '4cqi',
              borderRadius: '50%',
              border: '1px solid var(--card-border, #eaeaea)',
              color: content.bottomBanner.iconColor || 'var(--text-main)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center'
            }}>
              <DynamicIcon name={content.bottomBanner.icon} />
            </div>
          )}
          <div>
            {content.bottomBanner.label && renderText(content.bottomBanner.label, { fontSize: '0.8cqi', fontWeight: '800', textTransform: 'uppercase', letterSpacing: '0.05cqi', color: 'var(--text-main)', marginBottom: '0.3cqi' }, 'div')}
            {renderText(content.bottomBanner.text, { fontSize: '1.2cqi', color: 'var(--text-main)', fontWeight: '500', margin: 0 }, 'div')}
          </div>
        </div>
      )}

    </div>
  );
};
