import React from 'react';
import type { QuadrantMatrixSlideContent } from '../schemas';
import { renderText } from '../utils';
import { icons } from 'lucide-react';

interface Props {
  content: QuadrantMatrixSlideContent;
}

export const QuadrantMatrixSlide: React.FC<Props> = ({ content }) => {
  const renderIcon = (iconName: string, size: string, color: string) => {
    const IconComponent = (icons as any)[iconName];
    if (!IconComponent) return null;
    return <IconComponent style={{ width: size, height: size, color }} />;
  };

  return (
    <div style={{ width: '100%', height: '100%', padding: '3cqi 4.5cqi 0cqi 4.5cqi', background: '#fafafa', display: 'flex', flexDirection: 'column', fontFamily: "'Inter', sans-serif", boxSizing: 'border-box', position: 'relative', overflow: 'hidden' }}>
      
      {/* Background Swoosh */}
      <div style={{ position: 'absolute', top: '-30%', right: '-15%', width: '70cqi', height: '70cqi', background: '#faf5f0', borderRadius: '50%', zIndex: 0 }}></div>
      
      <div style={{ position: 'relative', zIndex: 1, flex: 1, display: 'flex', flexDirection: 'column' }}>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '0.2cqi' }}>
          {content.title && renderText(content.title, { fontSize: '3.5cqi', fontWeight: '800', color: '#0f172a', marginBottom: '0.2cqi' }, 'div')}
          {content.subtitle && renderText(content.subtitle, { fontSize: '1.5cqi', color: '#475569' }, 'div')}
        </div>
        
        {content.targetBadge && (
          <div style={{ marginTop: '1.5cqi', marginBottom: '2cqi' }}>
            <div style={{ display: 'inline-flex', alignItems: 'center', gap: '0.5cqi', padding: '0.5cqi 1cqi', background: 'white', border: '1px solid #e2e8f0', borderRadius: '999px', boxShadow: '0 1px 2px rgba(0,0,0,0.05)' }}>
              {content.targetBadge.icon && renderIcon(content.targetBadge.icon, '1.2cqi', '#ea580c')}
              <span style={{ fontWeight: 700, fontSize: '1cqi', color: '#0f172a' }}>{renderText(content.targetBadge.text, {}, 'span')}</span>
              <span style={{ fontWeight: 700, fontSize: '1cqi', color: '#ea580c' }}>{renderText(content.targetBadge.highlightText, {}, 'span')}</span>
            </div>
          </div>
        )}

        {/* Main Matrix Container */}
        <div style={{ backgroundColor: '#ffffff', padding: '2cqi', border: '1px solid #e2e8f0', borderRadius: '12px', display: 'flex', flexDirection: 'column', flex: 1, marginBottom: '3cqi', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)' }}>
          
          <div style={{ textAlign: 'center', marginBottom: '1cqi' }}>
            <h3 style={{ margin: 0, fontSize: '1.6cqi', fontWeight: 800, color: '#0f172a' }}>{renderText(content.cardTitle, {}, 'span')}</h3>
          </div>

          {/* Legend Pill */}
          <div style={{ display: 'flex', justifyContent: 'center', marginBottom: '2cqi' }}>
            <div style={{ display: 'inline-flex', gap: '1.5cqi', background: '#f8fafc', border: '1px solid #e2e8f0', borderRadius: '999px', padding: '0.5cqi 1.5cqi', fontSize: '0.9cqi', fontWeight: 600, color: '#475569' }}>
              {content.legendItems.map((item, i) => (
                <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '0.4cqi' }}>
                  <div style={{ width: '0.8cqi', height: '0.8cqi', borderRadius: '50%', background: item.color }}></div>
                  {item.isHighlight ? (
                    <span style={{ color: item.color }}>{renderText(item.text, {}, 'span')}</span>
                  ) : (
                    <span>{renderText(item.text, {}, 'span')}</span>
                  )}
                </div>
              ))}
            </div>
          </div>

          {/* The Quadrant Grid Area */}
          <div style={{ position: 'relative', flex: 1, border: '1px solid #cbd5e1', borderRadius: '8px', background: '#ffffff' }}>
            
            {/* Y Axis Line */}
            <div style={{ position: 'absolute', left: '50%', top: '5%', bottom: '5%', width: '2px', background: '#cbd5e1', transform: 'translateX(-50%)' }}>
              <div style={{ position: 'absolute', top: '-6px', left: '-4px', width: 0, height: 0, borderLeft: '5px solid transparent', borderRight: '5px solid transparent', borderBottom: '8px solid #cbd5e1' }}></div>
            </div>

            {/* X Axis Line */}
            <div style={{ position: 'absolute', top: '50%', left: '5%', right: '5%', height: '2px', background: '#cbd5e1', transform: 'translateY(-50%)' }}>
              <div style={{ position: 'absolute', left: '-6px', top: '-4px', width: 0, height: 0, borderTop: '5px solid transparent', borderBottom: '5px solid transparent', borderRight: '8px solid #cbd5e1' }}></div>
              <div style={{ position: 'absolute', right: '-6px', top: '-4px', width: 0, height: 0, borderTop: '5px solid transparent', borderBottom: '5px solid transparent', borderLeft: '8px solid #cbd5e1' }}></div>
            </div>

            {/* Labels */}
            <div style={{ position: 'absolute', left: '4%', top: '50%', transform: 'translate(-50%, -50%) rotate(-90deg)', background: 'white', padding: '0.4cqi 1cqi', border: '1px solid #cbd5e1', borderRadius: '999px', fontSize: '0.8cqi', fontWeight: 800, color: '#64748b', letterSpacing: '0.1em', zIndex: 10, whiteSpace: 'nowrap' }}>
              {content.yAxisLabel}
            </div>

            <div style={{ position: 'absolute', bottom: '6%', left: '50%', transform: 'translateX(-50%)', background: 'white', padding: '0.4cqi 1cqi', border: '1px solid #cbd5e1', borderRadius: '999px', fontSize: '0.8cqi', fontWeight: 800, color: '#64748b', letterSpacing: '0.1em', zIndex: 10, whiteSpace: 'nowrap' }}>
              {content.xAxisLabel}
            </div>

            {/* Quadrant Titles */}
            <div style={{ position: 'absolute', top: '4%', left: '12%' }}>
              <div style={{ fontSize: '0.8cqi', fontWeight: 800, color: content.quadrants.topLeft.color || '#94a3b8', letterSpacing: '0.1em' }}>{renderText(content.quadrants.topLeft.title, {}, 'span')}</div>
              <div style={{ fontSize: '1.5cqi', fontWeight: 800, color: content.quadrants.topLeft.subtitleColor || '#64748b' }}>{renderText(content.quadrants.topLeft.subtitle, {}, 'span')}</div>
            </div>

            <div style={{ position: 'absolute', bottom: '4%', left: '12%' }}>
              <div style={{ fontSize: '0.8cqi', fontWeight: 800, color: content.quadrants.bottomLeft.color || '#94a3b8', letterSpacing: '0.1em' }}>{renderText(content.quadrants.bottomLeft.title, {}, 'span')}</div>
              <div style={{ fontSize: '1.5cqi', fontWeight: 800, color: content.quadrants.bottomLeft.subtitleColor || '#64748b' }}>{renderText(content.quadrants.bottomLeft.subtitle, {}, 'span')}</div>
            </div>

            <div style={{ position: 'absolute', bottom: '4%', right: '6%', textAlign: 'right' }}>
              <div style={{ fontSize: '0.8cqi', fontWeight: 800, color: content.quadrants.bottomRight.color || '#94a3b8', letterSpacing: '0.1em' }}>{renderText(content.quadrants.bottomRight.title, {}, 'span')}</div>
              <div style={{ fontSize: '1.5cqi', fontWeight: 800, color: content.quadrants.bottomRight.subtitleColor || '#64748b' }}>{renderText(content.quadrants.bottomRight.subtitle, {}, 'span')}</div>
            </div>

            <div style={{ position: 'absolute', top: '4%', right: '6%', textAlign: 'right' }}>
              <div style={{ fontSize: '0.8cqi', fontWeight: 800, color: content.quadrants.topRight.color || '#94a3b8', letterSpacing: '0.1em' }}>{renderText(content.quadrants.topRight.title, {}, 'span')}</div>
              <div style={{ fontSize: '2.2cqi', fontWeight: 800, color: content.quadrants.topRight.subtitleColor || '#ea580c' }}>{renderText(content.quadrants.topRight.subtitle, {}, 'span')}</div>
            </div>

            {/* Bubbles */}
            {content.bubbles.map(bubble => {
              const size = bubble.size || (bubble.isHighlight ? '10cqi' : '7.5cqi');
              return (
                <div key={bubble.id} style={{ 
                  position: 'absolute', 
                  top: bubble.top, 
                  left: bubble.left, 
                  transform: 'translate(-50%, -50%)', 
                  width: size, 
                  height: size, 
                  borderRadius: '50%', 
                  background: bubble.color, 
                  display: 'flex', 
                  flexDirection: 'column', 
                  justifyContent: 'center', 
                  alignItems: 'center', 
                  color: 'white', 
                  boxShadow: bubble.isHighlight ? '0 10px 15px -3px rgba(0,0,0,0.2)' : '0 4px 6px -1px rgba(0, 0, 0, 0.1)' 
                }}>
                  <div style={{ fontWeight: bubble.isHighlight ? 800 : 700, fontSize: bubble.isHighlight ? '1.6cqi' : '1cqi' }}>{renderText(bubble.title, {}, 'span')}</div>
                  <div style={{ fontSize: bubble.isHighlight ? '0.8cqi' : '0.7cqi', opacity: 0.9, marginBottom: bubble.badge ? '0.5cqi' : '0' }}>{renderText(bubble.subtitle, {}, 'span')}</div>
                  {bubble.badge && (
                    <div style={{ background: 'white', color: bubble.color, padding: '0.2cqi 0.8cqi', borderRadius: '999px', fontSize: '0.7cqi', fontWeight: 800, textTransform: 'uppercase' }}>
                      {bubble.badge}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </div>
      </div>
    </div>
  );
};
