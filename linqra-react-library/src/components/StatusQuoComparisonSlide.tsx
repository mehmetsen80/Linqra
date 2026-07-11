import React from 'react';
import type { StatusQuoComparisonSlideContent } from '../schemas';
import { renderText } from '../utils';

interface StatusQuoComparisonSlideProps {
  content: StatusQuoComparisonSlideContent;
}

export const StatusQuoComparisonSlide: React.FC<StatusQuoComparisonSlideProps> = ({ content }) => {
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
      <div style={{ position: 'relative', zIndex: 2, textAlign: 'var(--title-align, center)' as any }}>
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

      {/* Comparison Container */}
      <div style={{
        display: 'flex',
        flex: 1,
        gap: '4cqi',
        position: 'relative',
        zIndex: 2,
        alignItems: 'stretch'
      }}>
        {/* VS Badge */}
        <div style={{
          position: 'absolute',
          left: '50%',
          top: '50%',
          transform: 'translate(-50%, -50%)',
          background: 'var(--bg-main, white)',
          border: '2px solid var(--card-border, #eaeaea)',
          borderRadius: '50%',
          width: '5cqi',
          height: '5cqi',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          fontWeight: '800',
          fontSize: '1.5cqi',
          color: 'var(--text-secondary)',
          zIndex: 10,
          boxShadow: '0 4px 10px rgba(0,0,0,0.1)'
        }}>
          VS
        </div>

        {/* Current State Box */}
        <div style={{
          flex: 1,
          background: 'var(--card-bg, #f8fafc)',
          borderRadius: '1.5cqi',
          border: '1px solid var(--card-border, #e2e8f0)',
          padding: '3cqi',
          display: 'flex',
          flexDirection: 'column',
          gap: '2cqi',
          ...(content.currentBox.style || {})
        }}>
          {renderText(content.currentBox.label, {
            fontSize: '2.8cqi',
            fontWeight: '800',
            textTransform: 'uppercase',
            letterSpacing: '0.1cqi',
            color: 'var(--text-secondary)',
            textAlign: 'center',
            borderBottom: '2px solid rgba(0,0,0,0.1)',
            paddingBottom: '1.5cqi',
            margin: 0
          }, 'h2')}
          
          <ul style={{ 
            listStyle: 'none', 
            padding: 0, 
            margin: 0, 
            display: 'flex', 
            flexDirection: 'column', 
            gap: '2cqi',
            flex: 1,
            justifyContent: 'center'
          }}>
            {content.currentBox.points.map((point, idx) => (
              <li key={`curr-${idx}`} style={{ display: 'flex', alignItems: 'flex-start', gap: '1.5cqi' }}>
                <span style={{ color: 'var(--text-tertiary)', fontSize: '2cqi', lineHeight: 1 }}>•</span>
                {renderText(point, { fontSize: '2.2cqi', color: 'var(--text-main)', lineHeight: 1.4, margin: 0 }, 'div')}
              </li>
            ))}
          </ul>
        </div>

        {/* Impact Box */}
        <div style={{
          flex: 1,
          background: 'var(--accent-bg, #fef2f2)',
          borderRadius: '1.5cqi',
          border: '1px solid var(--accent-border, #fecaca)',
          padding: '3cqi',
          display: 'flex',
          flexDirection: 'column',
          gap: '2cqi',
          ...(content.impactBox.style || {})
        }}>
          {renderText(content.impactBox.label, {
            fontSize: '2.8cqi',
            fontWeight: '800',
            textTransform: 'uppercase',
            letterSpacing: '0.1cqi',
            color: 'var(--accent-color, #ef4444)',
            textAlign: 'center',
            borderBottom: '2px solid rgba(239, 68, 68, 0.2)',
            paddingBottom: '1.5cqi',
            margin: 0
          }, 'h2')}
          
          <ul style={{ 
            listStyle: 'none', 
            padding: 0, 
            margin: 0, 
            display: 'flex', 
            flexDirection: 'column', 
            gap: '2cqi',
            flex: 1,
            justifyContent: 'center'
          }}>
            {content.impactBox.points.map((point, idx) => (
              <li key={`imp-${idx}`} style={{ display: 'flex', alignItems: 'flex-start', gap: '1.5cqi' }}>
                <span style={{ color: 'var(--accent-color, #ef4444)', fontSize: '2cqi', lineHeight: 1 }}>✗</span>
                {renderText(point, { fontSize: '2.2cqi', color: 'var(--text-main)', fontWeight: '500', lineHeight: 1.4, margin: 0 }, 'div')}
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
};
