import React from 'react';
import type { PainPointGridSlideContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

interface PainPointGridSlideProps {
  content: PainPointGridSlideContent;
}

export const PainPointGridSlide: React.FC<PainPointGridSlideProps> = ({ content }) => {
  const columns = content.columns || 2;

  return (
    <div style={{
      containerType: 'inline-size',
      padding: 'var(--slide-padding, 4cqi)',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      gap: 'var(--content-gap, 3cqi)',
      position: 'relative'
    }}>
      {/* Header */}
      <div style={{ position: 'relative', zIndex: 2, textAlign: 'var(--title-align, left)' as any }}>
        {renderText(content.title, { 
          fontSize: 'var(--title-font-size, 3.5cqi)', 
          fontWeight: '800', 
          margin: '0 0 1cqi 0', 
          color: 'var(--title-color, var(--text-main))',
          letterSpacing: '-0.02cqi' 
        }, 'h1')}
        {content.subtitle && renderText(content.subtitle, { 
          fontSize: '1.8cqi', 
          color: 'var(--subtitle-color, var(--text-secondary))', 
          margin: 0 
        }, 'div')}
      </div>

      {/* Grid */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${columns}, 1fr)`,
        gap: '2.5cqi',
        flex: 1,
        position: 'relative',
        zIndex: 2,
        alignContent: 'center'
      }}>
        {content.cards.map((card, idx) => (
          <div key={`ppcard-${idx}`} style={{
            background: 'var(--card-bg, rgba(255, 255, 255, 0.6))',
            borderRadius: '1.2cqi',
            border: '1px solid var(--card-border, #eaeaea)',
            padding: '2.5cqi',
            display: 'flex',
            flexDirection: 'column',
            gap: '1.5cqi',
            boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
            ...(card.cardStyle || {})
          }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
              <div style={{
                width: '4.5cqi',
                height: '4.5cqi',
                borderRadius: '0.8cqi',
                background: 'var(--icon-bg, rgba(0,0,0,0.04))',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: card.iconColor || 'var(--text-main)'
              }}>
                {card.icon && <DynamicIcon name={card.icon} />}
              </div>
              {card.metric && (
                <div style={{
                  background: 'var(--metric-bg, rgba(0,0,0,0.04))',
                  padding: '0.5cqi 1cqi',
                  borderRadius: '2cqi',
                }}>
                  {renderText(card.metric, { fontSize: '1.6cqi', fontWeight: '800', color: card.iconColor || 'var(--text-main)' }, 'span')}
                </div>
              )}
            </div>

            <div style={{ flex: 1 }}>
              {renderText(card.title, { 
                fontSize: '2.4cqi', 
                fontWeight: '700', 
                color: 'var(--text-main)', 
                marginBottom: '1cqi' 
              }, 'h3')}
              
              {renderText(card.description, { 
                fontSize: '1.6cqi', 
                color: 'var(--text-secondary)', 
                lineHeight: 1.5,
                margin: 0
              }, 'p')}
            </div>
          </div>
        ))}
      </div>
    </div>
  );
};
