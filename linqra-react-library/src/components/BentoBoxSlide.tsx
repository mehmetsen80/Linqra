import React from 'react';
import type { BentoBoxSlideContent, BentoBoxItem } from '../schemas';
import { renderText } from '../utils';
import * as LucideIcons from 'lucide-react';

export interface BentoBoxSlideProps {
  content: BentoBoxSlideContent;
}

export const BentoBoxSlide: React.FC<BentoBoxSlideProps> = ({ content }) => {
  const { title, subtitle, items, gridColumns = 4, portraitGridColumns = 1 } = content;

  // Use a unique class or container query name
  const containerId = React.useId().replace(/:/g, '');

  const renderIcon = (iconName?: string) => {
    if (!iconName) return null;
    const IconComponent = (LucideIcons as any)[iconName];
    if (!IconComponent) return null;
    return <IconComponent size="clamp(2rem, 5cqmin, 4rem)" strokeWidth={1.5} />;
  };

  const renderItem = (item: BentoBoxItem) => {
    const { colSpan = 1, rowSpan = 1, styleOptions = {} } = item;
    
    // Default styles
    const bg = styleOptions.background || (styleOptions.isGlassmorphic ? 'rgba(255, 255, 255, 0.1)' : '#ffffff');
    const color = styleOptions.color || (styleOptions.isGlassmorphic ? '#ffffff' : (item.imageUrl ? '#ffffff' : '#1e293b'));
    const border = styleOptions.isGlassmorphic ? '1px solid rgba(255,255,255,0.2)' : '1px solid #e2e8f0';
    const backdropFilter = styleOptions.isGlassmorphic ? 'blur(10px)' : 'none';
    const boxShadow = styleOptions.isGlassmorphic 
      ? '0 8px 32px 0 rgba(0, 0, 0, 0.37)' 
      : '0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03)';

    // Dynamic grid spans using CSS variables that can be overridden by the container query
    return (
      <div 
        key={item.id} 
        className={`bento-item-${containerId}`}
        style={{
          '--col-span': colSpan,
          '--row-span': rowSpan,
          containerType: 'inline-size',
          position: 'relative',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: item.metric ? 'center' : 'flex-end',
          alignItems: item.metric ? 'center' : 'flex-start',
          background: bg,
          color: color,
          borderRadius: '24px',
          padding: 'clamp(1.5rem, 4cqmin, 2.5rem)',
          border: border,
          boxShadow: boxShadow,
          backdropFilter: backdropFilter,
          WebkitBackdropFilter: backdropFilter,
          overflow: 'hidden',
          transition: 'transform 0.3s ease, box-shadow 0.3s ease',
          gridColumn: 'span var(--col-span)',
          gridRow: 'span var(--row-span)',
        } as React.CSSProperties}
      >
        {/* Background Image */}
        {item.imageUrl && (
          <div style={{
            position: 'absolute',
            top: 0, left: 0, right: 0, bottom: 0,
            backgroundImage: `url(${item.imageUrl})`,
            backgroundSize: 'cover',
            backgroundPosition: 'center',
            zIndex: 0,
            opacity: styleOptions.isGlassmorphic ? 0.4 : 1 // if it's a background image behind text
          }} />
        )}
        
        {/* Overlay gradient for readability if there is an image */}
        {item.imageUrl && !styleOptions.isGlassmorphic && (
          <div style={{
            position: 'absolute',
            top: 0, left: 0, right: 0, bottom: 0,
            background: 'linear-gradient(to top, rgba(0,0,0,0.8) 0%, rgba(0,0,0,0) 100%)',
            zIndex: 1
          }} />
        )}

        {/* Content */}
        <div style={{ position: 'relative', zIndex: 2, width: '100%', display: 'flex', flexDirection: 'column', height: '100%' }}>
          
          {/* Top section (Icon or Metric) */}
          <div style={{ display: 'flex', justifyContent: item.metric ? 'center' : 'flex-start', marginBottom: 'auto' }}>
            {item.iconName && (
              <div style={{ marginBottom: '1rem', color: color }}>
                {renderIcon(item.iconName)}
              </div>
            )}
            
            {item.metric && (
              <div style={{
                fontSize: 'clamp(2rem, 15cqi, 6rem)',
                fontWeight: 800,
                lineHeight: 1,
                marginBottom: item.title ? '1rem' : 0,
                color: color,
                textAlign: 'center'
              }}>
                {item.metric}
              </div>
            )}
          </div>

          {/* Bottom section (Text) */}
          {(item.title || item.description) && (
            <div style={{ textAlign: item.metric ? 'center' : 'left', marginTop: '1rem' }}>
              {item.title && renderText(item.title, { 
                fontSize: 'clamp(1.25rem, 3cqmin, 2rem)', 
                fontWeight: 700, 
                margin: '0 0 0.5rem 0',
                color: color 
              }, 'h3')}
              
              {item.description && renderText(item.description, { 
                fontSize: 'clamp(0.875rem, 1.8cqmin, 1.25rem)', 
                lineHeight: 1.5, 
                margin: 0,
                opacity: 0.9,
                color: color 
              }, 'p')}
            </div>
          )}
        </div>
      </div>
    );
  };

  return (
    <div style={{ width: '100%', height: '100%', flex: 1, display: 'flex', flexDirection: 'column', padding: 'clamp(1rem, 3cqmin, 2rem)', boxSizing: 'border-box' }}>
      
      {/* Dynamic Style Block for Container Queries */}
      <style>{`
        .bento-container-${containerId} {
          container-type: size;
          container-name: bentoGrid-${containerId};
          width: 100%;
          flex: 1;
          min-height: 0;
          display: grid;
          grid-template-columns: repeat(${gridColumns}, 1fr);
          gap: clamp(1rem, 2cqmin, 2rem);
          grid-auto-rows: 1fr;
        }

        /* Container Query: When the slide is narrow (like portrait), reset spans and columns */
        @container bentoGrid-${containerId} (max-aspect-ratio: 1.2/1) {
          .bento-container-${containerId} {
            grid-template-columns: repeat(${portraitGridColumns}, 1fr);
            grid-auto-rows: auto;
          }
          .bento-item-${containerId} {
            --col-span: 1 !important;
            --row-span: 1 !important;
            min-height: 250px;
          }
        }
        
        /* Interactive Hover */
        .bento-item-${containerId}:hover {
          transform: translateY(-4px);
          filter: brightness(1.05);
        }
      `}</style>

      {(title || subtitle) && (
        <div style={{ marginBottom: 'clamp(1rem, 2cqmin, 2rem)', textAlign: 'left', flexShrink: 0 }}>
          {title && renderText(title, { fontSize: 'clamp(1.75rem, 5cqmin, 3.5rem)', fontWeight: 700, margin: 0, color: '#1e293b' }, 'h2')}
          {subtitle && renderText(subtitle, { fontSize: 'clamp(1.1rem, 2.5cqmin, 1.5rem)', margin: '0.75rem 0 0 0', color: '#64748b' }, 'p')}
        </div>
      )}

      <div className={`bento-container-${containerId}`}>
        {items.map(renderItem)}
      </div>
    </div>
  );
};
