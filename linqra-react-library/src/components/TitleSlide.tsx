import React from 'react';
import type { TitleSlideContent, TitleBadge, TextContent } from '../schemas';
import { renderImage, renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: TitleSlideContent;
}

export const TitleSlide: React.FC<Props> = ({ content }) => {
  const renderBadge = (badge: TitleBadge, index: number) => {
    return (
      <div 
        key={`badge-${index}`} 
        style={{ 
          border: badge.border || '1px solid rgba(255,255,255,0.3)', 
          background: badge.background || 'rgba(255,255,255,0.15)', 
          padding: '1cqi 2.5cqi', 
          borderRadius: '9999px', 
          display: 'flex', 
          alignItems: 'center', 
          gap: '1cqi',
          ...(badge.style || {})
        }}
      >
        {badge.icon && (
          <span style={{ display: 'flex', alignItems: 'center', color: badge.textColor || 'inherit', width: badge.iconSize || '2.2cqi', height: badge.iconSize || '2.2cqi' }}>
            <DynamicIcon name={badge.icon} />
          </span>
        )}
        {renderText(badge.text, { fontSize: '1.35cqi', fontWeight: '600', margin: 0, color: badge.textColor || 'inherit' }, 'span')}
      </div>
    );
  };

  const renderTextArray = (items?: TextContent[], styleOverrides?: React.CSSProperties) => {
    if (!items || items.length === 0) return null;
    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5cqi', ...styleOverrides }}>
        {items.map((item, idx) => renderText(item, { margin: 0 }, "div", idx))}
      </div>
    );
  };

  const renderMixedArray = (items?: (TextContent | TitleBadge)[], styleOverrides?: React.CSSProperties) => {
    if (!items || items.length === 0) return null;
    return (
      <div style={{ display: 'flex', gap: '1.2cqi', flexWrap: 'wrap', ...styleOverrides }}>
        {items.map((item, idx) => {
          if (typeof item === 'object' && item !== null && 'text' in item && ('icon' in item || 'background' in item || 'border' in item)) {
            return renderBadge(item as TitleBadge, idx);
          }
          return renderText(item as TextContent, { margin: 0 }, "div", idx);
        })}
      </div>
    );
  };

  return (
    <div style={{ 
      containerType: 'inline-size', 
      padding: '5cqi', 
      textAlign: 'center', 
      height: '100%', 
      display: 'flex', 
      flexDirection: 'column', 
      justifyContent: 'center', 
      position: 'relative',
      overflow: 'hidden',
      boxSizing: 'border-box' 
    }}>
      {/* Background Elements */}
      {content.backgroundElements?.map((styleObj, idx) => (
        <div key={`bg-elem-${idx}`} style={{ position: 'absolute', ...styleObj }}></div>
      ))}

      {/* Main Content Area */}
      <div style={{ flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', zIndex: 1, marginTop: '-3cqi' }}>
        {content.image && (
          <div style={{ marginBottom: '4cqi', display: 'flex', justifyContent: 'center', alignItems: 'center' }}>
            {renderImage(content.image)}
          </div>
        )}
        {renderText(content.title, { fontSize: '4.8cqi', fontWeight: '700', margin: '0 0 1.5cqi 0', color: 'var(--text-main)', letterSpacing: '-0.05cqi', lineHeight: 1.2 }, 'h1')}
        {content.tagline && renderText(content.tagline, { fontSize: '3cqi', color: 'var(--text-main)', marginBottom: '1.5cqi', fontStyle: 'italic', fontWeight: 'bold' }, 'div')}
        {content.subtitle && renderText(content.subtitle, { fontSize: '2.6cqi', fontWeight: 'normal', margin: 0, color: 'var(--text-secondary)' }, 'h2')}
      </div>

      {/* Corner Grids & Badges Area */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end', zIndex: 1, marginTop: 'auto', width: '100%' }}>
        
        {/* Bottom Left */}
        <div style={{ textAlign: 'left', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end' }}>
          {renderTextArray(content.topLeftArea, { marginBottom: 'auto' })}
          {renderTextArray(content.bottomLeftArea)}
        </div>

        {/* Center Badges */}
        <div style={{ flex: '0 1 auto', display: 'flex', gap: '1.5cqi', alignItems: 'center', justifyContent: 'center', padding: '0 2cqi' }}>
          {content.centerBadges && content.centerBadges.map((badge, idx) => renderBadge(badge, idx))}
        </div>

        {/* Bottom Right */}
        <div style={{ textAlign: 'right', flex: 1, display: 'flex', flexDirection: 'column', justifyContent: 'flex-end', alignItems: 'flex-end' }}>
          {renderTextArray(content.topRightArea, { marginBottom: 'auto' })}
          {renderMixedArray(content.bottomRightArea)}
        </div>
      </div>
      
      {/* Legacy Footer Support */}
      {content.footer && renderText(content.footer, { position: 'absolute', bottom: '2cqi', left: '50%', transform: 'translateX(-50%)', fontSize: '1.5cqi', color: 'var(--text-tertiary)', zIndex: 1 }, 'footer')}
    </div>
  );
};
