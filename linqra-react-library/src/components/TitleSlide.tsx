import React from 'react';
import type { TitleSlideContent } from '../schemas';

import { renderImage, renderText } from '../utils';

interface Props {
  content: TitleSlideContent;
}

export const TitleSlide: React.FC<Props> = ({ content }) => {

  return (
    <div style={{ containerType: 'inline-size', padding: 'clamp(1rem, 5cqi, 4rem) clamp(0.5rem, 3cqi, 2rem)', textAlign: 'center', height: '100%', display: 'flex', flexDirection: 'column', justifyContent: 'center', overflowY: 'auto', boxSizing: 'border-box' }}>
      {renderImage(content.image)}
      {renderText(content.title, { fontSize: 'clamp(2.5rem, 8cqi, 4.5rem)', marginBottom: '1rem', color: 'var(--text-main)', lineHeight: 1.2 }, 'h1')}
      {renderText(content.tagline, { fontSize: 'clamp(1.5rem, 5cqi, 2.5rem)', color: 'var(--text-main)', marginBottom: '1rem', fontStyle: 'italic', fontWeight: 'bold' }, 'div')}
      {renderText(content.subtitle, { fontSize: 'clamp(1.2rem, 4cqi, 2rem)', fontWeight: 'normal', color: 'var(--text-secondary)' }, 'h2')}
      {renderText(content.footer, { marginTop: 'auto', fontSize: 'clamp(0.75rem, 1.5cqi, 1rem)', color: 'var(--text-tertiary)', paddingTop: '2rem' }, 'footer')}
    </div>
  );
};
