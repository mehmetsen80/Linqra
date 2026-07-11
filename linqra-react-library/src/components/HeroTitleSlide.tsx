import React from 'react';
import type { HeroTitleSlideContent } from '../schemas';
import { renderText } from '../utils';

interface Props {
  content: HeroTitleSlideContent;
}

export const HeroTitleSlide: React.FC<Props> = ({ content }) => {
  return (
    <div style={{
      containerType: 'inline-size',
      width: '100%',
      display: 'flex',
      flexDirection: 'column',
      justifyContent: 'center',
      alignItems: content.alignment === 'center' ? 'center' : content.alignment === 'right' ? 'flex-end' : 'flex-start',
      textAlign: content.alignment || 'left',
      boxSizing: 'border-box',
      ...(content.containerStyle || {})
    }}>
      {renderText(content.title, { 
        fontSize: '7cqi', 
        fontWeight: '800', 
        color: 'var(--text-main)', 
        lineHeight: 1.1, 
        margin: '0 0 1cqi 0',
        letterSpacing: '-0.02em'
      }, 'h1')}
      
      {content.subtitle && renderText(content.subtitle, { 
        fontSize: '2.5cqi', 
        color: 'var(--text-secondary)', 
        lineHeight: 1.4, 
        margin: 0,
        fontWeight: '400'
      }, 'h2')}
    </div>
  );
};
