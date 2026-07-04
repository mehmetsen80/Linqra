import React from 'react';

export interface ArrowProps {
  fromColor: string;
  toColor: string;
  vertical?: boolean;
  type?: 'classic' | 'chevron' | 'wedge' | 'line' | 'block' | 'curved' | 'looping' | '3d_ribbon_curve';
}

export const Arrow: React.FC<ArrowProps> = ({
  fromColor,
  toColor,
  vertical = false,
  type = 'classic'
}) => {
  const id = `arr-${fromColor.replace('#', '')}-${toColor.replace('#', '')}-${type}`;
  
  // Base SVG styles
  const svgStyle: React.CSSProperties = {
    display: 'var(--arrow-display, block)',
    filter: 'var(--arrow-shadow, drop-shadow(0px 2px 4px rgba(0,0,0,0.15)))',
    width: '100%',
    height: '100%',
    ...(vertical ? { maxHeight: '50px' } : { maxWidth: '50px' })
  };

  // Gradients for vertical and horizontal layouts
  const gradient = vertical ? (
    <linearGradient id={id} x1="0%" y1="0%" x2="0%" y2="100%">
      <stop offset="0%" stopColor={fromColor} />
      <stop offset="100%" stopColor={toColor} />
    </linearGradient>
  ) : (
    <linearGradient id={id} x1="0%" y1="0%" x2="100%" y2="0%">
      <stop offset="0%" stopColor={fromColor} />
      <stop offset="100%" stopColor={toColor} />
    </linearGradient>
  );

  const defs = <defs>{gradient}</defs>;
  const viewBox = vertical ? "0 0 30 50" : "0 0 50 30";
  const strokeColor = `var(--arrow-color, url(#${id}))`;
  const fillColor = `var(--arrow-head-color, var(--arrow-color, currentcolor))`;
  const strokeWidth = "var(--arrow-thickness, 4)";
  const strokeDasharray = "var(--arrow-dasharray, none)";

  if (type === 'line') {
    return (
      <svg viewBox={viewBox} style={svgStyle}>
        {defs}
        {vertical ? (
          <line x1="15" y1="0" x2="15" y2="50" stroke={strokeColor} strokeWidth={strokeWidth} strokeDasharray={strokeDasharray} />
        ) : (
          <line x1="0" y1="15" x2="50" y2="15" stroke={strokeColor} strokeWidth={strokeWidth} strokeDasharray={strokeDasharray} />
        )}
      </svg>
    );
  }

  if (type === 'chevron') {
    // Chevron > shape
    return (
      <svg viewBox={viewBox} style={svgStyle}>
        {defs}
        {vertical ? (
          <polyline points="5,15 15,35 25,15" fill="none" stroke={strokeColor} strokeWidth={strokeWidth} strokeDasharray={strokeDasharray} strokeLinecap="round" strokeLinejoin="round" />
        ) : (
          <polyline points="15,5 35,15 15,25" fill="none" stroke={strokeColor} strokeWidth={strokeWidth} strokeDasharray={strokeDasharray} strokeLinecap="round" strokeLinejoin="round" />
        )}
      </svg>
    );
  }

  if (type === 'block') {
    return (
      <svg viewBox={viewBox} style={svgStyle}>
        {defs}
        {vertical ? (
          <polygon points="10,0 20,0 20,30 25,30 15,50 5,30 10,30" fill={`url(#${id})`} />
        ) : (
          <polygon points="0,10 30,10 30,5 50,15 30,25 30,20 0,20" fill={`url(#${id})`} />
        )}
      </svg>
    );
  }

  if (type === 'curved') {
    return (
      <svg viewBox={viewBox} style={svgStyle}>
        {defs}
        {vertical ? (
          <>
            <path d="M 15,0 Q -10,20 15,40" fill="none" stroke={strokeColor} strokeWidth={strokeWidth} strokeDasharray={strokeDasharray} />
            <polygon points="5,34 15,50 25,34" fill={fillColor} style={{ color: toColor }} />
          </>
        ) : (
          <>
            <path d="M 0,15 Q 20,-10 40,15" fill="none" stroke={strokeColor} strokeWidth={strokeWidth} strokeDasharray={strokeDasharray} />
            <polygon points="34,5 50,15 34,25" fill={fillColor} style={{ color: toColor }} />
          </>
        )}
      </svg>
    );
  }

  if (type === 'looping') {
    return (
      <svg viewBox={viewBox} style={svgStyle}>
        {defs}
        {vertical ? (
          <>
            <path d="M 15,0 C 15,10 0,15 0,25 C 0,35 30,35 30,25 C 30,15 15,20 15,40" fill="none" stroke={strokeColor} strokeWidth={strokeWidth} strokeDasharray={strokeDasharray} />
            <polygon points="5,34 15,50 25,34" fill={fillColor} style={{ color: toColor }} />
          </>
        ) : (
          <>
            <path d="M 0,15 C 10,15 15,0 25,0 C 35,0 35,30 25,30 C 15,30 20,15 40,15" fill="none" stroke={strokeColor} strokeWidth={strokeWidth} strokeDasharray={strokeDasharray} />
            <polygon points="34,5 50,15 34,25" fill={fillColor} style={{ color: toColor }} />
          </>
        )}
      </svg>
    );
  }

  if (type === '3d_ribbon_curve') {
    const shadowId = `${id}-shadow`;
    const foldDefs = (
      <>
        {defs}
        <linearGradient id={shadowId} x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stopColor={fromColor} stopOpacity={0.8} />
          <stop offset="100%" stopColor="#000000" stopOpacity={0.4} />
        </linearGradient>
      </>
    );
    return (
      <svg viewBox={viewBox} style={svgStyle}>
        {foldDefs}
        {vertical ? (
          <path d="M 18,10 Q 5,22 11,35 L 5,35 L 15,50 L 25,35 L 19,35 Q 15,22 26,10 Z" fill={`url(#${id})`} />
        ) : (
          <path d="M 10,18 Q 22,5 35,11 L 35,5 L 50,15 L 35,25 L 35,19 Q 22,15 10,26 Z" fill={`url(#${id})`} />
        )}
      </svg>
    );
  }

  if (type === 'wedge') {
    // A thick tapering wedge
    return (
      <svg viewBox={viewBox} style={svgStyle}>
        {defs}
        {vertical ? (
          <polygon points="10,0 20,0 15,50" fill={`url(#${id})`} />
        ) : (
          <polygon points="0,10 0,20 50,15" fill={`url(#${id})`} />
        )}
      </svg>
    );
  }

  // Classic (default)
  return (
    <svg viewBox={viewBox} style={svgStyle}>
      {defs}
      {vertical ? (
        <>
          <line x1="15" y1="0" x2="15" y2="36" stroke={strokeColor} strokeWidth={strokeWidth} strokeDasharray={strokeDasharray} />
          <polygon points="5,34 15,50 25,34" fill={fillColor} style={{ color: toColor }} />
        </>
      ) : (
        <>
          <line x1="0" y1="15" x2="36" y2="15" stroke={strokeColor} strokeWidth={strokeWidth} strokeDasharray={strokeDasharray} />
          <polygon points="34,5 50,15 34,25" fill={fillColor} style={{ color: toColor }} />
        </>
      )}
    </svg>
  );
};
