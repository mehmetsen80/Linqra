import React from 'react';
import type { TeamProfileSlideContent, TeamMember } from '../schemas';
import { renderText } from '../utils';
import { Briefcase, MessageCircle, Code, Mail } from 'lucide-react';

export interface TeamProfileSlideProps {
  content: TeamProfileSlideContent;
}

export const TeamProfileSlide: React.FC<TeamProfileSlideProps> = ({ content }) => {
  const { title, subtitle, members, gridColumns } = content;

  // Helper to extract initials for fallback avatar
  const getInitials = (nameText: any) => {
    let strVal = '';
    if (typeof nameText === 'string') {
      strVal = nameText;
    } else if (nameText && nameText.text) {
      strVal = nameText.text;
    }
    
    if (!strVal) return '?';
    
    const parts = strVal.trim().split(' ').filter(Boolean);
    if (parts.length === 0) return '?';
    if (parts.length === 1) return parts[0].substring(0, 2).toUpperCase();
    return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
  };

  const renderMember = (member: TeamMember) => {
    return (
      <div 
        key={member.id} 
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          background: '#ffffff',
          borderRadius: '16px',
          padding: 'clamp(1rem, 2.5cqmin, 1.5rem)',
          border: '1px solid #e2e8f0',
          boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03)',
          transition: 'transform 0.2s ease-in-out, box-shadow 0.2s ease-in-out',
          cursor: 'default',
        }}
        onMouseEnter={(e) => {
          e.currentTarget.style.transform = 'translateY(-4px)';
          e.currentTarget.style.boxShadow = '0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05)';
        }}
        onMouseLeave={(e) => {
          e.currentTarget.style.transform = 'translateY(0)';
          e.currentTarget.style.boxShadow = '0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03)';
        }}
      >
        {/* Avatar */}
        <div style={{
          width: 'clamp(60px, 12cqmin, 100px)',
          height: 'clamp(60px, 12cqmin, 100px)',
          borderRadius: '50%',
          overflow: 'hidden',
          marginBottom: '1rem',
          background: '#1e293b', // Sleek dark slate fallback
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          color: '#ffffff',
          fontSize: 'clamp(1.25rem, 3cqmin, 2rem)',
          fontWeight: 600,
          border: '4px solid #f8fafc',
          boxShadow: '0 0 0 1px #e2e8f0',
          position: 'relative'
        }}>
          {member.imageUrl && (
            <img 
              src={member.imageUrl} 
              alt="Team Member" 
              style={{ width: '100%', height: '100%', objectFit: 'cover', position: 'absolute', zIndex: 1 }}
              onError={(e) => {
                // If image fails to load, hide it so the initials show underneath
                e.currentTarget.style.display = 'none';
              }}
            />
          )}
          <span style={{ position: 'relative', zIndex: 0 }}>{getInitials(member.name)}</span>
        </div>

        {/* Info */}
        <div style={{ textAlign: 'center', width: '100%' }}>
          {renderText(member.name, { fontSize: 'clamp(1rem, 2cqmin, 1.25rem)', fontWeight: 700, margin: '0 0 0.15rem 0', color: '#1e293b' }, 'h3')}
          {renderText(member.role, { fontSize: 'clamp(0.85rem, 1.5cqmin, 1rem)', fontWeight: 500, margin: '0 0 0.5rem 0', color: '#0ea5e9' }, 'div')}
          {member.bio && renderText(member.bio, { fontSize: 'clamp(0.75rem, 1.3cqmin, 0.9rem)', lineHeight: 1.4, margin: '0 0 0.75rem 0', color: '#64748b' }, 'p')}
        </div>

        {/* Socials */}
        {member.socials && Object.keys(member.socials).length > 0 && (
          <div style={{ display: 'flex', gap: '0.75rem', marginTop: 'auto', paddingTop: '0.75rem', borderTop: '1px solid #f1f5f9', width: '100%', justifyContent: 'center' }}>
            {member.socials.linkedin && (
              <a href={member.socials.linkedin} target="_blank" rel="noreferrer" style={{ color: '#94a3b8', transition: 'color 0.2s' }} onMouseEnter={(e) => e.currentTarget.style.color = '#0077b5'} onMouseLeave={(e) => e.currentTarget.style.color = '#94a3b8'}>
                <Briefcase size={20} />
              </a>
            )}
            {member.socials.twitter && (
              <a href={member.socials.twitter} target="_blank" rel="noreferrer" style={{ color: '#94a3b8', transition: 'color 0.2s' }} onMouseEnter={(e) => e.currentTarget.style.color = '#1da1f2'} onMouseLeave={(e) => e.currentTarget.style.color = '#94a3b8'}>
                <MessageCircle size={20} />
              </a>
            )}
            {member.socials.github && (
              <a href={member.socials.github} target="_blank" rel="noreferrer" style={{ color: '#94a3b8', transition: 'color 0.2s' }} onMouseEnter={(e) => e.currentTarget.style.color = '#333333'} onMouseLeave={(e) => e.currentTarget.style.color = '#94a3b8'}>
                <Code size={20} />
              </a>
            )}
            {member.socials.email && (
              <a href={`mailto:${member.socials.email}`} style={{ color: '#94a3b8', transition: 'color 0.2s' }} onMouseEnter={(e) => e.currentTarget.style.color = '#ea4335'} onMouseLeave={(e) => e.currentTarget.style.color = '#94a3b8'}>
                <Mail size={20} />
              </a>
            )}
          </div>
        )}
      </div>
    );
  };

  return (
    <div style={{ width: '100%', height: '100%', flex: 1, display: 'flex', flexDirection: 'column', padding: 'clamp(1rem, 3cqmin, 2rem)', boxSizing: 'border-box' }}>
      {(title || subtitle) && (
        <div style={{ marginBottom: 'clamp(1rem, 2cqmin, 2rem)', textAlign: 'center' }}>
          {title && renderText(title, { fontSize: 'clamp(1.75rem, 5cqmin, 3rem)', fontWeight: 700, margin: 0, color: '#1e293b' }, 'h2')}
          {subtitle && renderText(subtitle, { fontSize: 'clamp(1.1rem, 2.5cqmin, 1.5rem)', margin: '0.75rem 0 0 0', color: '#64748b' }, 'p')}
        </div>
      )}

      <div style={{
        flex: 1,
        display: 'grid',
        gridTemplateColumns: gridColumns 
          ? `repeat(${gridColumns}, 1fr)` 
          : 'repeat(auto-fit, minmax(min(180px, 22cqmin), 1fr))',
        gap: 'clamp(1rem, 2cqmin, 2rem)',
        alignContent: 'start',
        overflowY: 'auto',
        padding: '0.5rem' // to prevent shadow clipping
      }}>
        {members.map(renderMember)}
      </div>
    </div>
  );
};
