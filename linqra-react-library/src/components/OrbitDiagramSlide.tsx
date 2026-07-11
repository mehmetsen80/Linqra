import React from 'react';
import type { OrbitDiagramSlideContent } from '../schemas';
import { DynamicIcon } from './DynamicIcon';
import { renderText } from '../utils';

interface Props {
  content: OrbitDiagramSlideContent;
}

export const OrbitDiagramSlide: React.FC<Props> = ({ content }) => {
  const {
    centerIcon = 'Network',
    centerTitle = 'TITLE',
    centerSubtitle = 'SUBTITLE',
    themeColor = '#ea580c', // default brand orange
    size = '28cqi',
    rings,
    dots,
    showCrossLines = true,
    crossLineColor
  } = content;

  // Defaults if not provided
  const isOrange = themeColor === '#ea580c' || themeColor === '#f97316';
  
  const defaultRings = [
    { size: '92%', border: `1px solid ${isOrange ? '#ffedd5' : 'rgba(0,0,0,0.1)'}` },
    { size: '64%', border: `1px solid ${isOrange ? '#fed7aa' : 'rgba(0,0,0,0.15)'}` }
  ];
  const activeRings = rings || defaultRings;

  const defaultDots: NonNullable<OrbitDiagramSlideContent['dots']> = [
    { size: '3.5%', top: '7%', right: '25%' },
    { size: '2.8%', bottom: '21%', right: '9%' },
    { size: '4.2%', top: '35%', left: '3.5%' },
    { size: '3.2%', bottom: '10%', left: '21%' }
  ];
  const activeDots = dots || defaultDots;

  const activeCrossLineColor = crossLineColor || (isOrange ? '#ffedd5' : 'rgba(0,0,0,0.1)');

  return (
    <div style={{
      flex: `0 0 ${size}`,
      height: size,
      position: 'relative',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      ...(content.containerStyle || {})
    }}>
      
      {/* Rings */}
      {activeRings.map((ring, idx) => (
        <div key={`ring-${idx}`} style={{ position: 'absolute', width: ring.size, height: ring.size, border: ring.border, borderRadius: '50%' }}></div>
      ))}
      
      {/* Cross lines */}
      {showCrossLines && (
        <>
          <div style={{ position: 'absolute', width: '100%', height: '1px', background: activeCrossLineColor, transform: 'rotate(45deg)' }}></div>
          <div style={{ position: 'absolute', width: '100%', height: '1px', background: activeCrossLineColor, transform: 'rotate(-45deg)' }}></div>
        </>
      )}
      
      {/* Dots */}
      {activeDots.map((dot, idx) => (
        <div key={`dot-${idx}`} style={{ 
          position: 'absolute', 
          width: dot.size, 
          height: dot.size, 
          background: dot.color || themeColor, 
          borderRadius: '50%', 
          top: dot.top, 
          right: dot.right, 
          bottom: dot.bottom, 
          left: dot.left 
        }}></div>
      ))}

      {/* Center Circle */}
      <div style={{ 
        position: 'absolute', 
        width: '42%', 
        height: '42%', 
        background: themeColor, 
        borderRadius: '50%', 
        display: 'flex', 
        flexDirection: 'column', 
        alignItems: 'center', 
        justifyContent: 'center', 
        color: 'white', 
        boxShadow: `0 10px 25px -5px ${themeColor}80`
      }}>
        <DynamicIcon name={centerIcon} color="white" size="40%" />
        {renderText(centerTitle, { fontWeight: 800, fontSize: 'min(1.5cqi, 1rem)', marginTop: '4%' }, 'div')}
        {renderText(centerSubtitle, { fontSize: 'min(1cqi, 0.75rem)', fontWeight: 600 }, 'div')}
      </div>
    </div>
  );
};
