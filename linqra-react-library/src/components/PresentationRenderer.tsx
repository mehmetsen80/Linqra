import React from 'react';
import type { DeckData } from '../schemas';
import { renderText } from '../utils';

import { PresentationFooter } from './PresentationFooter';
import { sanitizeSlide } from '../slideSanitizer';

interface Props {
  deck: DeckData;
  theme?: 'light' | 'dark';
}

import { SlideComponentRenderer } from './SlideComponentRenderer';

export const PresentationRenderer: React.FC<Props> = ({ deck, theme = 'light' }) => {
  return (
    <div className={`linqra-presentation-renderer theme-${theme}`} style={{ width: '100%', height: '100%', display: 'flex', flexDirection: 'column' }}>
      <style>{`
        .linqra-presentation-renderer {
          --text-main: #333333;
          --text-secondary: #666666;
          --text-tertiary: #999999;
          --slide-bg: #ffffff;
          --card-bg: #fafafa;
          --card-border: #eaeaea;
          --card-radius: 12px;
          --card-padding: clamp(0.5rem, 3cqmin, 1.5rem);
          --card-gap: clamp(0.5rem, 2cqmin, 1.5rem);
          --content-gap: clamp(0.3rem, 1.5cqmin, 1rem);
          --step-connector-width: clamp(28px, 4cqmin, 52px);
          --diagram-node-bg: #e0f2fe;
          --diagram-node-border: #7dd3fc;
          --diagram-node-text: #0369a1;
          --diagram-edge: #94a3b8;
          --diagram-label-bg: #f8fafc;
          --diagram-node-radius: 5;
          font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
          color: var(--text-main);
          background: #e5e5e5;
          --font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
          --title-align: left;
          --title-font-size: clamp(1.5rem, 6cqmin, 3.5rem);
          --title-font-weight: bold;
          --body-align: left;
          --body-font-size: clamp(0.875rem, 3.5cqmin, 1.75rem);
          --body-line-height: 1.6;
          --letter-spacing: normal;
        }
        .linqra-presentation-renderer.theme-dark {
          --text-main: #ffffff;
          --text-secondary: #a1a1aa;
          --text-tertiary: #71717a;
          --slide-bg: #18181b;
          --card-bg: #27272a;
          --card-border: #3f3f46;
          --card-radius: 12px;
          --card-padding: clamp(0.5rem, 3cqmin, 1.5rem);
          --card-gap: clamp(0.5rem, 2cqmin, 1.5rem);
          --content-gap: clamp(0.3rem, 1.5cqmin, 1rem);
          --step-connector-width: clamp(28px, 4cqmin, 52px);
          --diagram-node-bg: #0c4a6e;
          --diagram-node-border: #0284c7;
          --diagram-node-text: #e0f2fe;
          --diagram-edge: #64748b;
          --diagram-label-bg: #0f172a;
          --diagram-node-radius: 5;
          color: var(--text-main);
          background: #09090b;
          --font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
          --title-align: left;
          --title-font-size: clamp(1.5rem, 6cqmin, 3.5rem);
          --title-font-weight: bold;
          --body-align: left;
          --body-font-size: clamp(0.875rem, 3.5cqmin, 1.75rem);
          --body-line-height: 1.6;
          --letter-spacing: normal;
        }
      `}</style>
      
      {/* For demonstration, we just stack slides. In a real player, you'd have pagination state */}
      <div style={{ padding: '1rem', borderBottom: '1px solid #eaeaea', background: '#f9f9f9', display: 'flex', justifyContent: 'space-between' }}>
        {renderText(deck.deckTitle, {}, 'strong')}
        <span>{deck.slides.length} slides</span>
      </div>
      
      <div style={{ flex: 1, overflowY: 'auto', padding: '2rem', background: '#eee' }}>
        {deck.slides.map(sanitizeSlide).map((slide, idx) => {
          const overrides = slide.styleOverrides || {};
          // Map all styleOverrides to scoped CSS Variables on the slide container.
          // Each component reads these vars, so every component is customisable per-slide.
          const dynamicStyles: any = {};
          if (overrides.backgroundColor)   dynamicStyles['--slide-bg']            = overrides.backgroundColor;
          if (overrides.textColor)          dynamicStyles['--text-main']           = overrides.textColor;
          if (overrides.textSecondary)      dynamicStyles['--text-secondary']      = overrides.textSecondary;
          if (overrides.subtitleColor)      dynamicStyles['--text-secondary']      = overrides.subtitleColor;
          if (overrides.slidePadding)       dynamicStyles['--slide-padding']          = overrides.slidePadding;
          if (overrides.contentGap)         dynamicStyles['--content-gap']            = overrides.contentGap;
          if (overrides.cardBg)             dynamicStyles['--card-bg']                = overrides.cardBg;
          if (overrides.cardBorder)         dynamicStyles['--card-border']            = overrides.cardBorder;
          if (overrides.cardRadius)         dynamicStyles['--card-radius']            = overrides.cardRadius;
          if (overrides.cardPadding)        dynamicStyles['--card-padding']           = overrides.cardPadding;
          if (overrides.cardGap)            dynamicStyles['--card-gap']               = overrides.cardGap;
          if (overrides.stepConnectorWidth) dynamicStyles['--step-connector-width']   = overrides.stepConnectorWidth;
          if (overrides.diagramNodeBg)      dynamicStyles['--diagram-node-bg']     = overrides.diagramNodeBg;
          if (overrides.diagramNodeBorder)  dynamicStyles['--diagram-node-border'] = overrides.diagramNodeBorder;
          if (overrides.diagramNodeText)    dynamicStyles['--diagram-node-text']   = overrides.diagramNodeText;
          if (overrides.diagramEdge)        dynamicStyles['--diagram-edge']        = overrides.diagramEdge;
          if (overrides.diagramLabelBg)     dynamicStyles['--diagram-label-bg']    = overrides.diagramLabelBg;
          if (overrides.diagramNodeRadius)  dynamicStyles['--diagram-node-radius'] = overrides.diagramNodeRadius;
          if (overrides.align) {
            dynamicStyles['--title-align'] = overrides.align;
            dynamicStyles['--body-align']  = overrides.align;
            dynamicStyles.textAlign        = overrides.align;
          }
          if (overrides.fontFamily)       dynamicStyles['--font-family']       = overrides.fontFamily;
          if (overrides.titleAlign)       dynamicStyles['--title-align']       = overrides.titleAlign;
          if (overrides.titleFontSize)    dynamicStyles['--title-font-size']   = overrides.titleFontSize;
          if (overrides.titleFontWeight)  dynamicStyles['--title-font-weight'] = overrides.titleFontWeight;
          if (overrides.bodyAlign)        dynamicStyles['--body-align']        = overrides.bodyAlign;
          if (overrides.bodyFontSize)     dynamicStyles['--body-font-size']    = overrides.bodyFontSize;
          if (overrides.bodyLineHeight)   dynamicStyles['--body-line-height']  = overrides.bodyLineHeight;
          if (overrides.letterSpacing)    dynamicStyles['--letter-spacing']    = overrides.letterSpacing;
          
          const snPos = overrides.slideNumberPosition || 'bottom-right';
          const slideNumberStyles: React.CSSProperties = {
            position: 'absolute',
            fontSize: '0.65rem',
            color: '#999',
            zIndex: 10,
          };
          if (snPos === 'bottom-right') { slideNumberStyles.bottom = '0.1rem'; slideNumberStyles.right = '0.25rem'; }
          else if (snPos === 'bottom-left') { slideNumberStyles.bottom = '0.1rem'; slideNumberStyles.left = '0.25rem'; }
          else if (snPos === 'top-right') { slideNumberStyles.top = '0.25rem'; slideNumberStyles.right = '0.25rem'; }
          else if (snPos === 'top-left') { slideNumberStyles.top = '0.25rem'; slideNumberStyles.left = '0.25rem'; }
          else if (snPos === 'hidden') { slideNumberStyles.display = 'none'; }

          const getAspectRatio = (ar?: string) => {
            if (ar === '4:3' || ar === '4/3') return '4/3';
            if (ar === '16:9' || ar === '16/9') return '16/9';
            if (ar === 'A4' || ar === 'a4' || ar === 'portrait') return '1 / 1.414';
            if (ar === 'Letter' || ar === 'letter') return '8.5 / 11';
            return '16/9'; // default
          };

          return (
          <div key={slide.id} className="deqra-slide-container" style={{ 
            containerType: 'size',
            background: 'var(--slide-bg)', 
            color: 'var(--text-main)',
            aspectRatio: getAspectRatio(deck.metadata?.aspectRatio),
            maxWidth: '1400px',
            margin: '0 auto 2rem auto',
            boxShadow: '0 10px 30px rgba(0,0,0,0.1)',
            position: 'relative',
            display: 'flex',
            flexDirection: 'column',
            ...dynamicStyles
          }}>
            <div style={{ flex: 1, minHeight: 0, position: 'relative', overflowY: 'auto', display: 'flex', flexDirection: 'column' }}>
              {deck.globalBackground && deck.globalBackground.length > 0 && (
                <div style={{ position: 'absolute', inset: 0, zIndex: 0, pointerEvents: 'none' }}>
                  {deck.globalBackground.map((bgSlide, i) => (
                    <SlideComponentRenderer key={`bg-${i}`} slide={bgSlide} />
                  ))}
                </div>
              )}
              <div style={{ position: 'relative', zIndex: 1, flex: 1, display: 'flex', flexDirection: 'column' }}>
                <SlideComponentRenderer slide={slide} />
              </div>
            </div>
            {deck.footer && !slide.hideFooter && (
              <PresentationFooter content={{ ...deck.footer, ...(slide.footerOverrides || {}) }} />
            )}
            <div style={slideNumberStyles}>{idx + 1}</div>
          </div>
          );
        })}
      </div>
    </div>
  );
};
