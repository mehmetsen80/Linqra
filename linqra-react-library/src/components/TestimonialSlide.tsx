import React from 'react';
import type { TestimonialSlideContent } from '../schemas';
import { renderText, renderImage } from '../utils';

interface Props {
  content: TestimonialSlideContent;
}

export const TestimonialSlide: React.FC<Props> = ({ content }) => {
  const { 
    quote, 
    author, 
    role, 
    avatar, 
    companyLogo, 
    companyName, 
    rating, 
    layoutVariant = 'centered',
    accentColor = '#3b82f6'
  } = content;

  const renderStars = () => {
    if (!rating) return null;
    const maxStars = 5;
    const safeRating = Math.max(1, Math.min(maxStars, Math.round(rating)));
    
    return (
      <div style={{ display: 'flex', gap: '0.25rem', marginBottom: 'clamp(1rem, 3cqmin, 2rem)', justifyContent: layoutVariant === 'centered' ? 'center' : 'flex-start' }}>
        {Array.from({ length: maxStars }).map((_, i) => (
          <svg key={i} width="clamp(20px, 4cqmin, 28px)" height="clamp(20px, 4cqmin, 28px)" viewBox="0 0 24 24" fill={i < safeRating ? '#f59e0b' : '#e2e8f0'}>
            <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" />
          </svg>
        ))}
      </div>
    );
  };

  return (
    <div style={{
      width: '100%',
      height: '100%',
      display: 'flex',
      flexDirection: layoutVariant === 'split' ? 'row' : 'column',
      justifyContent: 'center',
      alignItems: layoutVariant === 'split' ? 'stretch' : (layoutVariant === 'centered' ? 'center' : 'flex-start'),
      padding: 'clamp(1rem, 3cqmin, 4rem)',
      boxSizing: 'border-box',
      background: 'var(--slide-bg, #ffffff)',
      fontFamily: 'var(--font-family, inherit)',
      position: 'relative',
      containerType: 'inline-size'
    }}>
      
      {/* Decorative Quotation Mark */}
      <div style={{
        position: 'absolute',
        top: 'clamp(2rem, 5cqmin, 4rem)',
        left: layoutVariant === 'split' ? '50%' : (layoutVariant === 'centered' ? '50%' : 'clamp(2rem, 5cqmin, 4rem)'),
        transform: layoutVariant === 'centered' ? 'translateX(-50%)' : 'none',
        fontSize: 'clamp(8rem, 25cqmin, 20rem)',
        lineHeight: 1,
        fontFamily: 'Georgia, serif',
        color: `${accentColor}10`, // 10% opacity hex
        zIndex: 0,
        pointerEvents: 'none'
      }}>
        "
      </div>

      {layoutVariant === 'split' && avatar && (
        <div style={{
          flex: '0 0 40%',
          paddingRight: 'clamp(2rem, 5cqmin, 4rem)',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'center',
          alignItems: 'center'
        }}>
          {renderImage(avatar, undefined, {
            width: '100%',
            aspectRatio: '1/1',
            objectFit: 'cover',
            borderRadius: '24px',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)'
          })}
        </div>
      )}

      <div style={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'center',
        alignItems: layoutVariant === 'centered' ? 'center' : 'flex-start',
        textAlign: layoutVariant === 'centered' ? 'center' : 'left',
        position: 'relative',
        zIndex: 1,
        maxWidth: layoutVariant === 'split' ? '100%' : '800px',
        margin: layoutVariant === 'split' ? 0 : '0 auto'
      }}>
        {renderStars()}
        
        {/* The Quote */}
        {renderText(quote, {
          fontSize: 'clamp(1.5rem, 5cqmin, 3rem)',
          fontWeight: 600,
          color: '#1e293b',
          lineHeight: 1.4,
          marginBottom: 'clamp(2rem, 6cqmin, 4rem)'
        }, 'h2')}

        {/* Author Details */}
        <div style={{
          display: 'flex',
          alignItems: 'center',
          gap: 'clamp(1rem, 3cqmin, 1.5rem)',
          flexDirection: layoutVariant === 'centered' ? 'column' : 'row'
        }}>
          {layoutVariant !== 'split' && avatar && (
            renderImage(avatar, undefined, {
              width: 'clamp(60px, 10cqmin, 80px)',
              height: 'clamp(60px, 10cqmin, 80px)',
              borderRadius: '50%',
              objectFit: 'cover',
              boxShadow: '0 4px 6px -1px rgba(0,0,0,0.1)'
            })
          )}
          
          <div style={{ display: 'flex', flexDirection: 'column', alignItems: layoutVariant === 'centered' ? 'center' : 'flex-start' }}>
            {renderText(author, {
              fontSize: 'clamp(1.1rem, 3cqmin, 1.5rem)',
              fontWeight: 800,
              color: '#0f172a',
              margin: 0
            }, 'div')}
            
            {(role || companyName) && (
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', marginTop: '0.25rem', flexWrap: 'wrap', justifyContent: layoutVariant === 'centered' ? 'center' : 'flex-start' }}>
                {role && renderText(role, {
                  fontSize: 'clamp(0.9rem, 2cqmin, 1.1rem)',
                  color: '#64748b',
                  margin: 0
                }, 'span')}
                
                {role && companyName && <span style={{ color: '#cbd5e1' }}>•</span>}
                
                {companyName && renderText(companyName, {
                  fontSize: 'clamp(0.9rem, 2cqmin, 1.1rem)',
                  fontWeight: 600,
                  color: accentColor,
                  margin: 0
                }, 'span')}
              </div>
            )}
          </div>
          
          {companyLogo && (
            <div style={{ marginLeft: layoutVariant === 'centered' ? 0 : 'auto', marginTop: layoutVariant === 'centered' ? '1rem' : 0 }}>
              {renderImage(companyLogo, undefined, {
                height: 'clamp(30px, 5cqmin, 40px)',
                width: 'auto',
                objectFit: 'contain'
              })}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
