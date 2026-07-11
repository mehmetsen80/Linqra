import React from 'react';
import type { DetailedComparisonSlideContent, DetailedComparisonCardContent } from '../schemas';
import { TextBlockSlide } from './TextBlockSlide';
import * as LucideIcons from 'lucide-react';

interface Props {
  content: DetailedComparisonSlideContent;
}

export const DetailedComparisonSlide: React.FC<Props> = ({ content }) => {
  const renderText = (textObj: any, defaultStyle: React.CSSProperties = {}, Tag: any = 'div') => {
    if (!textObj) return null;
    if (typeof textObj === 'string') {
      return <Tag style={defaultStyle}>{textObj}</Tag>;
    }
    return <Tag style={{ ...defaultStyle, ...(textObj.style || {}) }}>{textObj.text}</Tag>;
  };

  const renderCard = (card: DetailedComparisonCardContent, alignRight: boolean) => {
    const IconComponent = card.headerIcon ? (LucideIcons as any)[card.headerIcon] : null;

    return (
      <div style={{
        flex: 1,
        backgroundColor: '#ffffff',
        padding: '1.5cqi',
        border: '1px solid #cbd5e1',
        borderRadius: '12px',
        position: 'relative',
        display: 'flex',
        flexDirection: 'column'
      }}>
        {/* Optional Overlapping Badge */}
        {card.badge && (
          <div style={{
            position: 'absolute',
            top: '-1cqi',
            [alignRight ? 'right' : 'left']: '2cqi',
            background: card.badge.backgroundColor || '#dc2626',
            color: card.badge.color || 'white',
            padding: '0.3cqi 1cqi',
            borderRadius: '999px',
            fontSize: '0.7cqi',
            fontWeight: 800,
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
            boxShadow: `0 2px 4px ${card.badge.backgroundColor ? card.badge.backgroundColor + '40' : 'rgba(220,38,38,0.3)'}`
          }}>
            {renderText(card.badge.text, {}, 'span')}
          </div>
        )}

        {/* Card Header */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: card.headerRightText || card.headerRightValue ? 'space-between' : 'flex-start',
          gap: '1cqi',
          marginBottom: '1cqi',
          marginTop: '0.2cqi'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1cqi' }}>
            {IconComponent && (
              <div style={{
                width: '3.5cqi',
                height: '3.5cqi',
                borderRadius: '50%',
                background: card.headerIconBackgroundColor || '#fee2e2',
                border: card.headerIconBackgroundColor === '#f1f5f9' ? '1px solid #e2e8f0' : 'none',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center'
              }}>
                <IconComponent size="1.8cqi" color={card.headerIconColor || '#ef4444'} />
              </div>
            )}
            <div>
              {renderText(card.headerTitle, { fontWeight: 800, fontSize: '1.2cqi', color: '#0f172a' })}
              {card.headerSubtitle && renderText(card.headerSubtitle, { fontWeight: 600, fontSize: '1cqi', color: '#ef4444' })}
            </div>
          </div>
          {(card.headerRightText || card.headerRightValue) && (
            <div style={{ textAlign: 'right' }}>
              {card.headerRightText && renderText(card.headerRightText, { fontWeight: 800, fontSize: '0.8cqi', color: '#0f172a', textTransform: 'uppercase', letterSpacing: '0.1em' })}
              {card.headerRightValue && renderText(card.headerRightValue, { fontWeight: 800, fontSize: '1.2cqi', color: '#334155' })}
            </div>
          )}
        </div>

        {/* Card Items */}
        <div style={{ display: 'flex', flexDirection: 'column', justifyContent: 'space-between', flex: 1 }}>
          {card.items.map((item, index) => {
            const ItemIcon = (LucideIcons as any)[item.icon];
            return (
              <div key={index} style={{
                border: '1px solid #e2e8f0',
                borderRadius: '8px',
                padding: '0.6cqi 1cqi',
                display: 'flex',
                alignItems: 'flex-start',
                gap: '1cqi'
              }}>
                {ItemIcon && (
                  <div style={{ marginTop: '0.2cqi' }}>
                    <ItemIcon size="1.2cqi" color={item.iconColor || '#ef4444'} />
                  </div>
                )}
                <div>
                  {renderText(item.title, { fontWeight: 800, fontSize: '0.9cqi', color: '#334155' })}
                  {renderText(item.description, { fontSize: '0.9cqi', color: '#64748b' })}
                </div>
              </div>
            );
          })}
        </div>
      </div>
    );
  };

  return (
    <div style={{
      width: '100%',
      height: '100%',
      padding: '1.5cqi 4.5cqi 0cqi 4.5cqi',
      background: '#fafafa',
      display: 'flex',
      flexDirection: 'column',
      fontFamily: "'Inter', sans-serif",
      boxSizing: 'border-box',
      position: 'relative',
      overflow: 'hidden'
    }}>
      {/* Background Swoosh */}
      <div style={{
        position: 'absolute',
        top: '-30%',
        right: '-15%',
        width: '70cqi',
        height: '70cqi',
        background: '#faf5f0',
        borderRadius: '50%',
        zIndex: 0
      }}></div>
      
      <div style={{ position: 'relative', zIndex: 1, flex: 1, display: 'flex', flexDirection: 'column' }}>
        <TextBlockSlide content={{
          blocks: [
            { text: typeof content.title === 'string' ? content.title : content.title.text, style: { fontSize: '3cqi', fontWeight: '800', color: '#0f172a', marginBottom: '0.2cqi' } },
            ...(content.subtitle ? [{ text: typeof content.subtitle === 'string' ? content.subtitle : content.subtitle.text, style: { fontSize: '1.2cqi', color: '#475569' } }] : [])
          ]
        }} />
        
        <div style={{ position: 'relative', flex: 1, display: 'flex', marginTop: '1.5cqi', marginBottom: '1.5cqi', alignItems: 'stretch', gap: '4cqi' }}>
          
          {/* VS Badge */}
          <div style={{
            position: 'absolute',
            left: '50%',
            top: '50%',
            transform: 'translate(-50%, -50%)',
            width: '3.5cqi',
            height: '3.5cqi',
            borderRadius: '50%',
            background: '#f1f5f9',
            border: '4px solid white',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            fontWeight: 800,
            color: '#94a3b8',
            fontSize: '1cqi',
            zIndex: 10,
            boxShadow: '0 4px 6px -1px rgba(0,0,0,0.05)'
          }}>
            VS
          </div>

          {renderCard(content.leftCard, false)}
          {renderCard(content.rightCard, true)}
        </div>
      </div>
    </div>
  );
};
