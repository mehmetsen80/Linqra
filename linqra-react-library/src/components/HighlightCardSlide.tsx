import React from 'react';
import type { HighlightCardSlideContent, TextContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: HighlightCardSlideContent;
}

export const HighlightCardSlide: React.FC<Props> = ({ content }) => {
  const renderBadge = (badge: TextContent, idx: number) => {
    const customStyle = typeof badge === 'string' ? {} : (badge.style || {});
    return (
      <div key={`badge-${idx}`} style={{
        padding: '0.6cqi 1.2cqi',
        borderRadius: '0.3cqi',
        background: 'var(--badge-bg, rgba(0, 0, 0, 0.05))',
        color: 'var(--badge-text, var(--text-main))',
        fontWeight: 'bold',
        fontSize: '1.4cqi',
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
      width: '100%',
      height: '100%',
      boxSizing: 'border-box'
    }}>
      <div style={{
        background: 'var(--card-bg, white)',
        borderRadius: '1.2cqi',
        border: '1px solid var(--card-border, #eaeaea)',
        padding: '2cqi 2.5cqi',
        position: 'relative',
        boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        height: '100%',
        boxSizing: 'border-box',
        ...(content.cardStyle || {})
      }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
          <div style={{ flex: 1 }}>
            {renderText(content.category, { fontSize: '2cqi', fontWeight: '800', textTransform: 'uppercase', letterSpacing: '0.05cqi', marginBottom: '0.5cqi', color: 'var(--text-secondary)' }, 'div')}
            {renderText(content.value, { fontSize: '7cqi', fontWeight: '800', color: 'var(--text-main)', margin: 0, lineHeight: 1.1 }, 'div')}
          </div>
          {content.icon && (
            <div style={{ background: content.iconBg || 'transparent', borderRadius: '0.8cqi', color: content.iconColor || 'var(--text-tertiary)', width: '4.5cqi', height: '4.5cqi', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <DynamicIcon name={content.icon} />
            </div>
          )}
        </div>
        <div style={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: '1cqi', marginTop: '1.5cqi' }}>
          {renderText(content.description, { fontSize: '1.4cqi', color: 'var(--text-secondary)', margin: 0, lineHeight: 1.5, whiteSpace: 'pre-wrap' }, 'div')}
        </div>
        {content.badges && content.badges.length > 0 && (
          <div style={{ display: 'flex', gap: '0.8cqi', flexWrap: 'wrap', marginTop: '1.5cqi' }}>
            {content.badges.map((b, i) => renderBadge(b, i))}
          </div>
        )}
      </div>
    </div>
  );
};
