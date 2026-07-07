import React from 'react';
import type { ComparisonSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: ComparisonSlideContent;
}

export const ComparisonSlide: React.FC<Props> = ({ content }) => {
  const { leftColumn, rightColumn } = content;

  const renderText = (textObj: any, defaultStyle: React.CSSProperties = {}, Tag: any = 'span') => {
    if (!textObj) return null;
    if (typeof textObj === 'string') return <Tag style={defaultStyle}>{textObj}</Tag>;
    return <Tag style={{ ...defaultStyle, ...(textObj.style || {}) }}>{textObj.text}</Tag>;
  };

  const renderColumn = (colData: any, isRight: boolean = false) => {
    if (!colData) return null;
    return (
      <div style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
      }}>
        {/* Column Header */}
        {renderText(colData.heading, {
          fontSize: '1.5rem',
          fontWeight: 700,
          color: isRight ? '#2563eb' : '#1e293b',
          marginBottom: '1.5rem',
          paddingBottom: '0.5rem',
          borderBottom: `2px solid ${isRight ? '#bfdbfe' : '#e2e8f0'}`,
        }, 'h3')}
        
        {/* Column Items */}
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          {(colData.items || []).map((item: any, idx: number) => {
            const itemColor = item.color || (isRight ? '#3b82f6' : '#ef4444');
            const bgLight = isRight ? '#eff6ff' : '#fef2f2';
            
            return (
              <div key={idx} style={{
                display: 'flex',
                alignItems: 'flex-start',
                backgroundColor: '#ffffff',
                border: '1px solid #e5e7eb',
                borderRadius: '8px',
                padding: '1.25rem',
                boxShadow: '0 2px 4px rgba(0,0,0,0.02)',
              }}>
                {/* Icon Box */}
                <div style={{
                  width: '48px',
                  height: '48px',
                  borderRadius: '12px',
                  backgroundColor: bgLight,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  marginRight: '1rem',
                  flexShrink: 0
                }}>
                  {item.icon ? (
                    <DynamicIcon name={item.icon} size={24} color={itemColor} />
                  ) : (
                    <div style={{ width: '24px', height: '24px', backgroundColor: itemColor, borderRadius: '4px' }} />
                  )}
                </div>
                
                {/* Text Content */}
                <div style={{ display: 'flex', flexDirection: 'column' }}>
                  {renderText(item.title, {
                    fontSize: '1.1rem',
                    fontWeight: 700,
                    color: '#1e293b',
                    marginBottom: '0.25rem',
                    lineHeight: 1.2
                  }, 'h4')}
                  {item.description && renderText(item.description, {
                    fontSize: '0.95rem',
                    color: '#64748b',
                    lineHeight: 1.5,
                    marginTop: '0.25rem'
                  }, 'p')}
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
      display: 'flex',
      flexDirection: 'column',
      padding: 'clamp(1rem, 3cqmin, 2rem) clamp(1.5rem, 4cqmin, 3rem)',
      boxSizing: 'border-box'
    }}>
      {/* Title Section */}
      {(content.title || content.subtitle) && (
        <div style={{ marginBottom: 'clamp(1rem, 2cqmin, 2rem)' }}>
          {content.title && renderText(content.title, { fontSize: '2.5rem', fontWeight: 800, color: '#111', margin: 0 }, 'h1')}
          {content.subtitle && renderText(content.subtitle, { fontSize: '1.2rem', color: '#666', marginTop: '0.5rem' }, 'p')}
        </div>
      )}

      {/* Main Grid */}
      <div style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'row',
        gap: 'clamp(1.5rem, 3cqmin, 3rem)',
        overflowY: 'auto',
        paddingRight: '0.5rem'
      }}>
        {renderColumn(leftColumn, false)}
        {renderColumn(rightColumn, true)}
      </div>
    </div>
  );
};
