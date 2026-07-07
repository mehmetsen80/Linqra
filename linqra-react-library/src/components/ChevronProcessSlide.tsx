import React, { useRef, useState, useEffect } from 'react';
import type { ChevronProcessSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';
import { renderText } from '../utils';

export interface ChevronProcessSlideProps {
  content: ChevronProcessSlideContent;
}

export const ChevronProcessSlide: React.FC<ChevronProcessSlideProps> = ({ content }) => {
  const steps = content.steps || [];
  const containerRef = useRef<HTMLDivElement>(null);
  const [dimensions, setDimensions] = useState({ width: 0, height: 0 });

  useEffect(() => {
    if (!containerRef.current) return;
    const observer = new ResizeObserver((entries) => {
      setDimensions({
        width: entries[0].contentRect.width,
        height: entries[0].contentRect.height
      });
    });
    observer.observe(containerRef.current);
    return () => observer.disconnect();
  }, []);

  if (steps.length === 0) return null;

  return (
    <div style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column', padding: 'clamp(1rem, 3cqmin, 2rem)', boxSizing: 'border-box' }}>
      <div style={{ marginBottom: '2rem' }}>
        {renderText(content.title, { fontSize: '2.5rem', fontWeight: 700, margin: 0, color: '#1e293b' }, 'h2')}
        {renderText(content.subtitle, { fontSize: '1.25rem', margin: '0.5rem 0 0 0', color: '#64748b' }, 'p')}
      </div>

      <div ref={containerRef} style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        {dimensions.width > 0 && steps.map((step, idx) => {
          const isFirst = idx === 0;
          const isLast = idx === steps.length - 1;
          const color = step.color || '#3b82f6';
          
          // Allocation math: Total width = (Sum of Ratios * unitWidth) - ((N-1) * ARROW_HEAD). 
          // Therefore: unitWidth = (Total Width + (N-1) * ARROW_HEAD) / totalRatio
          const ARROW_HEAD = 40; // Pixels for the interlocking arrow tip
          const totalRatio = steps.reduce((sum, s) => sum + (s.widthRatio || 1), 0);
          const unitWidth = (dimensions.width + (steps.length - 1) * ARROW_HEAD) / totalRatio;
          
          const stepWidth = (step.widthRatio || 1) * unitWidth;
          
          // Fixed height for the chevron bar
          const H = 140; 
          const W = stepWidth;

          // Determine the SVG path in exact pixels
          let d = "";
          if (isFirst) {
            d = `M 0 0 L ${W - ARROW_HEAD} 0 L ${W} ${H/2} L ${W - ARROW_HEAD} ${H} L 0 ${H} Z`;
          } else if (isLast) {
            d = `M 0 0 L ${W} 0 L ${W} ${H} L 0 ${H} L ${ARROW_HEAD} ${H/2} Z`;
          } else {
            d = `M 0 0 L ${W - ARROW_HEAD} 0 L ${W} ${H/2} L ${W - ARROW_HEAD} ${H} L 0 ${H} L ${ARROW_HEAD} ${H/2} Z`;
          }

          return (
            <div key={idx} style={{ 
              width: `${stepWidth}px`, 
              height: `${H}px`, 
              position: 'relative',
              marginLeft: isFirst ? 0 : `-${ARROW_HEAD}px` // Overlap them exactly by the arrow head depth!
            }}>
              {/* Chevron SVG */}
              <div style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', zIndex: steps.length - idx }}>
                {/* zIndex decreases so left overlaps right slightly for a clean seam, or right overlaps left, depending on design. Here, left overlaps right. */}
                <svg width="100%" height="100%" style={{ overflow: 'visible' }}>
                  {/* Subtle drop shadow for depth between overlapping chevrons */}
                  <defs>
                    <filter id={`shadow-${idx}`} x="-20%" y="-20%" width="140%" height="140%">
                      <feDropShadow dx="2" dy="0" stdDeviation="4" floodOpacity="0.15" />
                    </filter>
                  </defs>
                  <path 
                    d={d} 
                    fill={color} 
                    stroke={color}
                    strokeWidth={(content.cornerRadius !== undefined ? content.cornerRadius : 8) * 2} // Double the radius for stroke rounding
                    strokeLinejoin="round"
                    filter={!isLast ? `url(#shadow-${idx})` : undefined} 
                  />
                </svg>
              </div>

              {/* Content */}
              <div style={{ 
                position: 'relative', 
                zIndex: steps.length + 1, 
                display: 'flex', 
                flexDirection: 'column', 
                alignItems: 'center', 
                justifyContent: 'center',
                height: '100%',
                paddingLeft: isFirst ? '2rem' : `${ARROW_HEAD + 10}px`,
                paddingRight: isLast ? '2rem' : `${ARROW_HEAD + 10}px`,
                textAlign: 'center',
                color: '#ffffff'
              }}>
                {step.icon && <DynamicIcon name={step.icon} size={32} color="#ffffff" />}
                {renderText(step.title, { margin: step.icon ? '0.5rem 0 0.25rem' : '0 0 0.25rem', color: '#ffffff', fontSize: '1.25rem', fontWeight: 600 }, 'h3')}
                {renderText(step.description, { margin: 0, color: 'rgba(255,255,255,0.9)', fontSize: '0.9rem', lineHeight: 1.3 }, 'p')}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
};
