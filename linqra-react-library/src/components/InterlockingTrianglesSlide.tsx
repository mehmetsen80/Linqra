import React, { useRef, useState, useEffect } from 'react';
import type { InterlockingTrianglesSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';
import { renderText } from '../utils';

export interface InterlockingTrianglesSlideProps {
  content: InterlockingTrianglesSlideContent;
}

export const InterlockingTrianglesSlide: React.FC<InterlockingTrianglesSlideProps> = ({ content }) => {
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

  // The math for tessellating triangles:
  // Each step takes up exactly width W.
  // Because they overlap by W/2 horizontally (forming the zig-zag), 
  // the total bounding width needed for N steps is (N + 1) * (W / 2).
  // So: dimensions.width = (N + 1) * (W / 2)  =>  W = 2 * dimensions.width / (N + 1)
  
  const N = steps.length;
  const W = dimensions.width > 0 ? (2 * dimensions.width) / (N + 1) : 0;
  
  const H = Math.min(120, dimensions.height * 0.35); // Scale down if container is short
  const R = Math.min(55, dimensions.height * 0.15);  
  const START_Y = R + Math.min(60, dimensions.height * 0.15); // Balance point

  // Helper to darken a hex color for the left-side shade
  const darkenHex = (hex: string, amount = 30) => {
    let color = hex.replace('#', '');
    if (color.length === 3) color = color.split('').map(c => c + c).join('');
    const num = parseInt(color, 16);
    let r = (num >> 16) - amount;
    let g = ((num >> 8) & 0x00FF) - amount;
    let b = (num & 0x0000FF) - amount;
    r = r < 0 ? 0 : r;
    g = g < 0 ? 0 : g;
    b = b < 0 ? 0 : b;
    return `#${(g | (b << 8) | (r << 16)).toString(16).padStart(6, '0')}`;
  };

  return (
    <div style={{ width: '100%', height: '100%', flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', padding: 'clamp(0.5rem, 2cqmin, 1rem) clamp(1rem, 3cqmin, 2rem)', boxSizing: 'border-box' }}>
      {(content.title || content.subtitle) && (
        <div style={{ marginBottom: '1rem' }}>
          {content.title && renderText(content.title, { fontSize: `clamp(1.1rem, 3cqmin, 2.5rem)`, fontWeight: 700, margin: 0, color: '#1e293b' }, 'h2')}
          {content.subtitle && renderText(content.subtitle, { fontSize: `clamp(0.85rem, 2cqmin, 1.25rem)`, margin: '0.5rem 0 0 0', color: '#64748b' }, 'p')}
        </div>
      )}

      <div ref={containerRef} style={{ flex: 1, position: 'relative' }}>
        {dimensions.width > 0 && (
          <>
            {/* Background SVG Canvas spanning the exact calculated width/height */}
            <svg 
              style={{ position: 'absolute', top: 0, left: 0, width: '100%', height: '100%', overflow: 'visible', zIndex: 0 }}
            >
              <defs>
                <filter id="triangle-shadow" x="-20%" y="-20%" width="140%" height="140%">
                  <feDropShadow dx="0" dy="4" stdDeviation="6" floodOpacity="0.15" />
                </filter>
                <filter id="circle-shadow" x="-30%" y="-30%" width="160%" height="160%">
                  <feDropShadow dx="0" dy="6" stdDeviation="8" floodOpacity="0.25" />
                </filter>
                <clipPath id="bounds-clip">
                  <rect x="-50%" y={START_Y - 2} width="200%" height={H + 4} />
                </clipPath>
              </defs>

              {steps.map((step, idx) => {
                const isDown = idx % 2 === 0;
                const baseX = idx * (W / 2);
                
                const providedColor = step.color || '#3b82f6';
                // Premium default: if no nodeBgColor is provided, the node gets the bright provided color, 
                // and the triangle becomes a darkened premium backdrop.
                const actualNodeBgColor = step.nodeBgColor || providedColor;
                const baseColor = step.nodeBgColor ? providedColor : darkenHex(providedColor, 40);

                const flatStyle = content.flatStyle !== false; // Default to true
                let darkColor = darkenHex(baseColor, 40);

                if (flatStyle) {
                  darkColor = baseColor;
                }
                
                // --- Triangle Coordinates ---
                let dLeft = "";
                let dRight = "";
                let dOuter = "";
                
                if (isDown) {
                  // Flat edge at START_Y. Points down to START_Y + H at center W/2.
                  dLeft = `M ${baseX} ${START_Y} L ${baseX + W/2} ${START_Y} L ${baseX + W/2} ${START_Y + H} Z`;
                  dRight = `M ${baseX + W/2} ${START_Y} L ${baseX + W} ${START_Y} L ${baseX + W/2} ${START_Y + H} Z`;
                  dOuter = `M ${baseX} ${START_Y} L ${baseX + W} ${START_Y} L ${baseX + W/2} ${START_Y + H} Z`;
                } else {
                  // Flat edge at START_Y + H. Points up to START_Y at center W/2.
                  dLeft = `M ${baseX} ${START_Y + H} L ${baseX + W/2} ${START_Y + H} L ${baseX + W/2} ${START_Y} Z`;
                  dRight = `M ${baseX + W/2} ${START_Y + H} L ${baseX + W} ${START_Y + H} L ${baseX + W/2} ${START_Y} Z`;
                  dOuter = `M ${baseX} ${START_Y + H} L ${baseX + W} ${START_Y + H} L ${baseX + W/2} ${START_Y} Z`;
                }

                const currentR = step.nodeRadius || R;
                const circleCX = baseX + W/2;
                const circleCY = isDown ? START_Y : START_Y + H;
                const gap = content.gapWidth !== undefined ? content.gapWidth : 16;
                const isHideDivider = step.hideDivider !== false;

                return (
                  <g key={`svg-${idx}`} filter="url(#triangle-shadow)">
                    {isHideDivider ? (
                      <>
                        {/* Outer full triangle for the right-side base color */}
                        <path d={dOuter} fill={baseColor} stroke="none" />
                        {/* Left half over top to blend perfectly at the vertical seam */}
                        <path d={dLeft} fill={darkColor} stroke="none" />
                      </>
                    ) : (
                      <>
                        {/* Left Half Triangle (Dark Shade) */}
                        <path d={dLeft} fill={darkColor} stroke="none" />
                        {/* Right Half Triangle (Base Shade) */}
                        <path d={dRight} fill={baseColor} stroke="none" />
                        {/* Explicit Vertical Divider */}
                        <line x1={baseX + W/2} y1={START_Y} x2={baseX + W/2} y2={START_Y + H} stroke="#ffffff" strokeWidth="4" />
                      </>
                    )}
                    
                    {/* Extra thick diagonal gap between this step and the previous step */}
                    {idx > 0 && gap > 0 && (
                      <g clipPath="url(#bounds-clip)">
                        <line 
                          x1={baseX} 
                          y1={isDown ? START_Y : START_Y + H} 
                          x2={baseX + W/2} 
                          y2={isDown ? START_Y + H : START_Y} 
                          stroke="#ffffff" 
                          strokeWidth={gap} 
                        />
                      </g>
                    )}

                    {/* Leftmost border for the first step */}
                    {idx === 0 && (
                      <g clipPath="url(#bounds-clip)">
                        <line 
                          x1={baseX} 
                          y1={isDown ? START_Y : START_Y + H} 
                          x2={baseX + W/2} 
                          y2={isDown ? START_Y + H : START_Y} 
                          stroke="#ffffff" 
                          strokeWidth="4" 
                        />
                      </g>
                    )}

                    {/* Rightmost border for the last step */}
                    {idx === steps.length - 1 && (
                      <g clipPath="url(#bounds-clip)">
                        <line 
                          x1={baseX + W/2} 
                          y1={isDown ? START_Y + H : START_Y} 
                          x2={baseX + W} 
                          y2={isDown ? START_Y : START_Y + H} 
                          stroke="#ffffff" 
                          strokeWidth="4" 
                        />
                      </g>
                    )}
                    
                    {/* Overlapping Circle */}
                    {content.softCircle && !step.nodeBgColor && (
                      <circle cx={circleCX} cy={circleCY} r={currentR} fill="#ffffff" stroke="none" />
                    )}
                    <circle 
                      cx={circleCX} 
                      cy={circleCY} 
                      r={currentR} 
                      fill={actualNodeBgColor} 
                      fillOpacity={content.softCircle && !step.nodeBgColor ? 0.15 : 1}
                      stroke={baseColor} 
                      strokeWidth="12" 
                      filter="url(#circle-shadow)" 
                    />
                  </g>
                );
              })}
            </svg>

            {/* HTML Overlays for Text and Icons */}
            {steps.map((step, idx) => {
              const isDown = idx % 2 === 0;
              const baseX = idx * (W / 2);
              const currentR = step.nodeRadius || R;
              const cx = baseX + W/2;
              const cy = isDown ? START_Y : START_Y + H;
              
              const providedColor = step.color || '#3b82f6';
              const baseColor = step.nodeBgColor ? providedColor : darkenHex(providedColor, 40);

              // If a custom bg color is provided, default to white text unless you want to pass a custom text color property later
              const textIconColor = step.nodeBgColor ? '#ffffff' : (content.outlineCircle || content.softCircle ? baseColor : '#ffffff');

              return (
                <React.Fragment key={`html-${idx}`}>
                  {/* Icon & Title Inside the Circle */}
                  <div style={{
                    position: 'absolute',
                    left: `${cx - currentR}px`,
                    top: `${cy - currentR}px`,
                    width: `${currentR * 2}px`,
                    height: `${currentR * 2}px`,
                    display: 'flex',
                    flexDirection: 'column',
                    alignItems: 'center',
                    justifyContent: 'center',
                    zIndex: 2,
                    color: textIconColor,
                    textAlign: 'center',
                    padding: '8px'
                  }}>
                    {step.icon && <DynamicIcon name={step.icon} size={24} color={textIconColor} />}
                    <div style={{ 
                      fontSize: 'clamp(0.65rem, 1.5cqmin, 0.8rem)', 
                      fontWeight: 700, 
                      lineHeight: 1.1, 
                      marginTop: step.icon ? '4px' : '0' 
                    }}>
                      {renderText(step.title, {}, 'span')}
                    </div>
                  </div>

                  {/* Description Text Outside the Flow */}
                  <div style={{
                    position: 'absolute',
                    left: `${cx - W/2}px`,
                    width: `${W}px`,
                    top: isDown ? `${START_Y - currentR - 15}px` : `${START_Y + H + currentR + 15}px`,
                    transform: isDown ? 'translateY(-100%)' : 'none',
                    textAlign: 'center',
                    padding: '0 1rem',
                    zIndex: 1
                  }}>
                    {renderText(step.description, { margin: 0, color: '#475569', fontSize: 'clamp(0.7rem, 1.5cqmin, 0.85rem)', lineHeight: 1.4 }, 'p')}
                  </div>
                </React.Fragment>
              );
            })}
          </>
        )}
      </div>
    </div>
  );
};
