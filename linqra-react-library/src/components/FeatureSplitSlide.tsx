import React from 'react';
import type { FeatureSplitSlideContent } from '../schemas';
import { renderText } from '../utils';
import { DynamicIcon } from './DynamicIcon';

interface Props {
  content: FeatureSplitSlideContent;
}

export const FeatureSplitSlide: React.FC<Props> = ({ content }) => {
  const { leftColumn, rightColumn } = content;

  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'flex-start',
      padding: 'clamp(0.5rem, 3cqmin, 4rem)',
      boxSizing: 'border-box',
      background: 'var(--slide-bg, #ffffff)',
      fontFamily: 'var(--font-family, inherit)'
    }}>
      <div style={{
        display: 'flex',
        flexDirection: 'row',
        alignItems: 'stretch',
        gap: 'clamp(1rem, 5cqmin, 4rem)'
      }}>
      
      {/* Left Column (List) */}
      <div style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
      }}>
        <div style={{ 
          display: 'flex', 
          alignItems: 'center', 
          gap: '1rem', 
          marginBottom: 'clamp(0.5rem, 2cqmin, 2.5rem)',
          paddingBottom: 'clamp(0.2rem, 1cqmin, 1rem)'
        }}>
          {leftColumn.icon && (
            <div style={{
              width: 'clamp(36px, 6cqmin, 48px)',
              height: 'clamp(36px, 6cqmin, 48px)',
              borderRadius: '12px',
              backgroundColor: leftColumn.iconBg || '#eff6ff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0
            }}>
              <DynamicIcon name={leftColumn.icon} size={24} color={leftColumn.iconColor || "#2563eb"} />
            </div>
          )}
          {renderText(leftColumn.heading, {
            fontSize: 'clamp(1.25rem, 3cqmin, 1.75rem)',
            fontWeight: 800,
            color: '#0f172a',
            margin: 0
          }, 'h2')}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 'clamp(0.5rem, 2cqmin, 1.5rem)' }}>
          {leftColumn.items.map((item, idx) => (
            <div key={idx} style={{ display: 'flex', alignItems: 'flex-start', gap: '1rem' }}>
              {item.icon && (
                <div style={{
                  width: 'clamp(32px, 5cqmin, 40px)',
                  height: 'clamp(32px, 5cqmin, 40px)',
                  borderRadius: '10px',
                  backgroundColor: item.iconBg || '#eff6ff',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  flexShrink: 0,
                  marginTop: '0.1rem'
                }}>
                  <DynamicIcon name={item.icon} size={20} color={item.iconColor || "#2563eb"} />
                </div>
              )}
              <div style={{ display: 'flex', flexDirection: 'column' }}>
                {renderText(item.title, {
                  fontSize: 'clamp(0.95rem, 1.5cqmin, 1.1rem)',
                  fontWeight: 700,
                  color: '#1e293b',
                  marginBottom: '0.25rem'
                }, 'div')}
                {item.description && renderText(item.description, {
                  fontSize: 'clamp(0.85rem, 1.25cqmin, 0.95rem)',
                  color: '#475569',
                  lineHeight: 1.5
                }, 'div')}
              </div>
            </div>
          ))}
        </div>
      </div>

      {/* Vertical Separator */}
      <div style={{
        width: '2px',
        borderLeft: '2px dotted #cbd5e1',
        margin: '0 clamp(0.5rem, 2cqmin, 1rem)',
        ...(content.separatorStyle || {})
      }} />

      {/* Right Column (Cards) */}
      <div style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
      }}>
        <div style={{ 
          display: 'flex', 
          alignItems: 'center', 
          gap: '1rem', 
          marginBottom: 'clamp(0.5rem, 2cqmin, 2.5rem)',
          paddingBottom: 'clamp(0.2rem, 1cqmin, 1rem)'
        }}>
          {rightColumn.icon && (
            <div style={{
              width: 'clamp(36px, 6cqmin, 48px)',
              height: 'clamp(36px, 6cqmin, 48px)',
              borderRadius: '12px',
              backgroundColor: rightColumn.iconBg || '#eff6ff',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              flexShrink: 0
            }}>
              <DynamicIcon name={rightColumn.icon} size={24} color={rightColumn.iconColor || "#2563eb"} />
            </div>
          )}
          {renderText(rightColumn.heading, {
            fontSize: 'clamp(1.25rem, 3cqmin, 1.75rem)',
            fontWeight: 800,
            color: '#0f172a',
            margin: 0
          }, 'h2')}
        </div>

        <div style={{ display: 'flex', flexDirection: 'column', gap: 'clamp(0.25rem, 1.5cqmin, 1.25rem)' }}>
          {rightColumn.items.map((item, idx) => {
            const iconColor = item.color || '#f59e0b';
            
            return (
              <div key={idx} style={{
                display: 'flex',
                alignItems: 'center',
                gap: '1rem',
                backgroundColor: '#ffffff',
                border: '1px solid #f1f5f9',
                borderRadius: '12px',
                padding: 'clamp(0.5rem, 1.5cqmin, 1.25rem)',
                boxShadow: '0 4px 6px -1px rgba(0,0,0,0.05), 0 2px 4px -1px rgba(0,0,0,0.03)',
                ...(item.cardStyle || {})
              }}>
                {item.icon && (
                  <div style={{
                    width: 'clamp(36px, 6cqmin, 48px)',
                    height: 'clamp(36px, 6cqmin, 48px)',
                    borderRadius: '10px',
                    backgroundColor: `${iconColor}15`,
                    border: `1px solid ${iconColor}33`,
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    flexShrink: 0
                  }}>
                    <DynamicIcon name={item.icon} size={22} color={iconColor} />
                  </div>
                )}
                <div style={{ flex: 1 }}>
                  {renderText(item.text, {
                    fontSize: 'clamp(0.9rem, 1.5cqmin, 1rem)',
                    color: '#334155',
                    lineHeight: 1.5
                  }, 'div')}
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
