import React from 'react';
import type { TimelineSlideContent, TextContent } from '../schemas';
import { renderImage, renderText } from '../utils';

interface Props {
  content: TimelineSlideContent;
}

const VB_W = 1000;
const VB_H = 440;
const LINE_Y = 220;
const H_PAD = 70;
const DOT_R = 10;
const CARD_W = 175;
const CONNECTOR_LEN = 40;
const CARD_MARGIN = 6;

// Base font sizes (in SVG coordinate units)
const FS_DATE  = 14;
const FS_TITLE = 17;
const FS_DESC  = 13;

const DEFAULT_COLORS = ['#6366f1', '#ec4899', '#14b8a6', '#f59e0b', '#22c55e', '#3b82f6', '#ef4444'];

function wrapText(text: string, maxChars: number): string {
  return text.length > maxChars ? text.substring(0, maxChars - 1) + '…' : text;
}

function extractText(item: TextContent | undefined): string {
  if (!item) return '';
  return typeof item === 'object' ? item.text : item;
}

function applyWrap(item: TextContent | undefined, newText: string): TextContent | undefined {
  if (!item) return undefined;
  if (typeof item === 'object') return { ...item, text: newText };
  return newText;
}

export const TimelineSlide: React.FC<Props> = ({ content }) => {
  const events = content.events;
  const n = events.length;

  const xPos = (i: number): number =>
    H_PAD + (n > 1 ? (i / (n - 1)) * (VB_W - 2 * H_PAD) : (VB_W - 2 * H_PAD) / 2);

  return (
    <div
      style={{
        containerType: 'size',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        boxSizing: 'border-box',
        padding: 'var(--slide-padding, clamp(0.5rem, 3cqmin, 2rem))',
        fontFamily: 'var(--font-family)',
        overflow: 'hidden',
      }}
    >
      {renderText(content.title, { margin: 0, marginBottom: '0.2rem', color: 'var(--text-main)', fontSize: 'min(var(--title-font-size, clamp(1.4rem, 5cqmin, 2.5rem)), 7cqmin)', fontWeight: 'var(--title-font-weight, bold)' as any, textAlign: 'var(--title-align, left)' as any, lineHeight: 1.1 }, 'h1')}
      {renderImage(content.image)}
      {renderText(content.subtitle, { margin: '0 0 var(--content-gap, 0.4rem) 0', color: 'var(--text-secondary)', fontSize: 'min(var(--body-font-size, clamp(0.8rem, 2.5cqmin, 1.1rem)), 3cqmin)' }, 'p')}
      <div style={{ flex: 1, minHeight: 0, minWidth: 0 }}>
        <svg
          viewBox={`0 0 ${VB_W} ${VB_H}`}
          preserveAspectRatio="xMidYMid meet"
          style={{ width: '100%', height: '100%', overflow: 'visible' }}
        >
          <defs>
            <linearGradient id="tl-line-grad" x1="0%" y1="0%" x2="100%" y2="0%">
              <stop offset="0%" stopColor="#a1a1aa" stopOpacity="0.2" />
              <stop offset="30%" stopColor="#a1a1aa" stopOpacity="0.9" />
              <stop offset="70%" stopColor="#a1a1aa" stopOpacity="0.9" />
              <stop offset="100%" stopColor="#a1a1aa" stopOpacity="0.2" />
            </linearGradient>
          </defs>

          {/* Central timeline track */}
          <rect
            x={H_PAD}
            y={LINE_Y - 2}
            width={VB_W - 2 * H_PAD}
            height={4}
            rx={2}
            fill="url(#tl-line-grad)"
          />

          {events.map((event, i) => {
            const x = xPos(i);
            const isAbove = i % 2 === 0;
            const color = event.color || DEFAULT_COLORS[i % DEFAULT_COLORS.length];
            const cardBgColor = event.cardBg || 'var(--card-bg)';
            const titleFill   = event.titleColor || 'var(--text-main)';
            const descFill    = event.descColor  || 'var(--text-secondary)';
            const scale = event.fontSize ?? 1.0;
            const fsDate  = FS_DATE  * scale;
            const fsTitle = FS_TITLE * scale;
            const fsDesc  = FS_DESC  * scale;
            const rawDesc = extractText(event.description);
            const rawTitle = extractText(event.title);
            const hasDesc = Boolean(rawDesc);
            // Card height grows with font scale
            const cardH = hasDesc
              ? Math.round(120 * scale)
              : Math.round(95 * scale);
            const cardWScaled = Math.round(CARD_W * Math.max(1, scale * 0.9));

            const cardX = Math.max(4, Math.min(VB_W - cardWScaled - 4, x - cardWScaled / 2));

            const connY1 = isAbove ? LINE_Y - DOT_R - 1 : LINE_Y + DOT_R + 1;
            const connY2 = isAbove
              ? LINE_Y - DOT_R - CONNECTOR_LEN - cardH - CARD_MARGIN
              : LINE_Y + DOT_R + CONNECTOR_LEN + CARD_MARGIN;
            const cardY = connY2;
            const cx = cardX + cardWScaled / 2; // center x of card

            return (
              <g key={i}>
                {/* Dashed connector */}
                <line
                  x1={x} y1={connY1}
                  x2={x} y2={connY2}
                  stroke={color}
                  strokeWidth={1.5}
                  strokeDasharray="5 3"
                  opacity={0.65}
                />
                {/* Dot glow */}
                <circle cx={x} cy={LINE_Y} r={DOT_R + 6} fill={color} opacity={0.12} />
                <circle cx={x} cy={LINE_Y} r={DOT_R + 2} fill="none" stroke={color} strokeWidth={1.5} opacity={0.4} />
                <circle cx={x} cy={LINE_Y} r={DOT_R} fill={color} />
                <circle cx={x - 2} cy={LINE_Y - 2} r={DOT_R * 0.3} fill="white" opacity={0.6} />

                {/* Card shadow */}
                <rect x={cardX + 2} y={cardY + 3} width={cardWScaled} height={cardH} rx={8} fill="rgba(0,0,0,0.08)" />
                {/* Card background */}
                <rect x={cardX} y={cardY} width={cardWScaled} height={cardH} rx={8} fill={cardBgColor} stroke={color} strokeWidth={1.5} />
                {/* Accent top bar */}
                <rect x={cardX} y={cardY} width={cardWScaled} height={6} rx={8} fill={color} />
                <rect x={cardX} y={cardY + 3} width={cardWScaled} height={4} fill={color} />

                {/* Date pill */}
                <rect x={cardX + 8} y={cardY + 12} width={cardWScaled - 16} height={Math.round(fsDate + 8)} rx={5} fill={color} opacity={0.15} />
                {renderText(event.date, { fill: color, fontFamily: 'inherit', fontWeight: '700', fontSize: fsDate, letterSpacing: '0.03em' }, (props: any) => <text x={cx} y={cardY + 12 + Math.round(fsDate)} textAnchor="middle" {...props} />)}

                {/* Event title */}
                {renderText(applyWrap(event.title, wrapText(rawTitle, Math.round(22 / scale))), { fill: titleFill, fontFamily: 'inherit', fontWeight: '700', fontSize: fsTitle }, (props: any) => <text x={cx} y={cardY + 12 + Math.round(fsDate) + 12 + Math.round(fsTitle)} textAnchor="middle" {...props} />)}

                {/* Description line 1 */}
                {hasDesc && renderText(applyWrap(event.description, wrapText(rawDesc, Math.round(26 / scale))), { fill: descFill, fontFamily: 'inherit', fontSize: fsDesc, opacity: 0.85 }, (props: any) => <text x={cx} y={cardY + 12 + Math.round(fsDate) + 12 + Math.round(fsTitle) + 10 + Math.round(fsDesc)} textAnchor="middle" {...props} />)}
                {hasDesc && rawDesc.length > Math.round(26 / scale) && renderText(applyWrap(event.description, wrapText(rawDesc.substring(Math.round(26 / scale)), Math.round(26 / scale))), { fill: descFill, fontFamily: 'inherit', fontSize: fsDesc, opacity: 0.85 }, (props: any) => <text x={cx} y={cardY + 12 + Math.round(fsDate) + 12 + Math.round(fsTitle) + 10 + Math.round(fsDesc) + Math.round(fsDesc) + 4} textAnchor="middle" {...props} />)}
              </g>
            );
          })}
        </svg>
      </div>
    </div>
  );
};
