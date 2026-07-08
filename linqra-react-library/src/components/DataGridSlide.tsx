import React, { useState } from 'react';
import type { DataGridSlideContent } from '../schemas';
import { renderText } from '../utils';

export interface DataGridSlideProps {
  content: DataGridSlideContent;
}

export const DataGridSlide: React.FC<DataGridSlideProps> = ({ content }) => {
  const { title, subtitle, columns, rows, styleOptions = {} } = content;
  const [hoveredRowId, setHoveredRowId] = useState<string | null>(null);

  const {
    headerBg = '#f8fafc',
    headerColor = '#475569',
    rowHoverBg = '#f1f5f9',
    striped = true,
    compact = false
  } = styleOptions;

  const cellPadding = compact ? 'clamp(0.5rem, 1cqmin, 0.75rem)' : 'clamp(0.75rem, 1.5cqmin, 1.25rem)';
  const fontSize = compact ? 'clamp(0.75rem, 1.8cqmin, 0.95rem)' : 'clamp(0.85rem, 2cqmin, 1.05rem)';

  // Helper to format numeric values (coloring + and -)
  const renderNumericCell = (textVal: any) => {
    let strVal = '';
    if (typeof textVal === 'string') {
      strVal = textVal;
    } else if (textVal && textVal.text) {
      strVal = textVal.text;
    }

    if (!strVal) return renderText(textVal, { fontSize, margin: 0 }, 'span');

    // Detect if starts with + or -
    const isPositive = strVal.trim().startsWith('+');
    const isNegative = strVal.trim().startsWith('-');
    
    let color = undefined;
    if (isPositive) color = '#10b981'; // Green
    if (isNegative) color = '#ef4444'; // Red

    if (color) {
      if (typeof textVal === 'string') {
        return <span style={{ color, fontWeight: 600 }}>{strVal}</span>;
      } else {
        return renderText({ ...textVal, style: { ...textVal.style, color, fontWeight: 600 } }, { fontSize, margin: 0 }, 'span');
      }
    }

    return renderText(textVal, { fontSize, margin: 0 }, 'span');
  };

  return (
    <div style={{ width: '100%', height: '100%', flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column', padding: 'clamp(1.5rem, 4cqmin, 3rem)' }}>
      {(title || subtitle) && (
        <div style={{ marginBottom: '2rem' }}>
          {title && renderText(title, { fontSize: 'clamp(1.5rem, 4cqmin, 2.5rem)', fontWeight: 700, margin: 0, color: '#1e293b' }, 'h2')}
          {subtitle && renderText(subtitle, { fontSize: 'clamp(1rem, 2cqmin, 1.25rem)', margin: '0.5rem 0 0 0', color: '#64748b' }, 'p')}
        </div>
      )}

      <div style={{ 
        flex: '0 1 auto', 
        minHeight: 0, 
        overflowX: 'auto', 
        overflowY: 'auto',
        background: '#ffffff',
        borderRadius: '12px',
        border: '1px solid #e2e8f0',
        boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)'
      }}>
        <table style={{ width: '100%', borderCollapse: 'collapse', textAlign: 'left' }}>
          <thead style={{ position: 'sticky', top: 0, zIndex: 10, background: headerBg }}>
            <tr>
              {columns.map((col, idx) => (
                <th 
                  key={col.key || idx} 
                  style={{
                    padding: cellPadding,
                    borderBottom: '2px solid #cbd5e1',
                    textAlign: col.align || 'left',
                    width: col.width || 'auto',
                    fontWeight: 600
                  }}
                >
                  {renderText(col.header, { fontSize: 'clamp(0.75rem, 1.8cqmin, 0.95rem)', color: headerColor, textTransform: 'uppercase', letterSpacing: '0.05em', margin: 0 }, 'span')}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((row, rowIdx) => {
              const isStriped = striped && rowIdx % 2 !== 0;
              const isHovered = hoveredRowId === row.id;
              
              let rowBg = '#ffffff';
              if (row.isHighlighted) {
                rowBg = '#f8fafc'; // Or a subtle highlight color
              } else if (isStriped) {
                rowBg = '#fafafa';
              }
              
              if (isHovered) rowBg = rowHoverBg;

              return (
                <tr 
                  key={row.id}
                  onMouseEnter={() => setHoveredRowId(row.id)}
                  onMouseLeave={() => setHoveredRowId(null)}
                  style={{ 
                    background: rowBg, 
                    transition: 'background 0.2s',
                    fontWeight: row.isHighlighted ? 600 : 400
                  }}
                >
                  {columns.map((col) => {
                    const cellData = row.cells[col.key];
                    return (
                      <td 
                        key={`${row.id}-${col.key}`}
                        style={{
                          padding: cellPadding,
                          borderBottom: '1px solid #e2e8f0',
                          textAlign: col.align || 'left',
                          color: '#334155'
                        }}
                      >
                        {col.isNumeric 
                          ? renderNumericCell(cellData)
                          : renderText(cellData, { fontSize, margin: 0 }, 'span')
                        }
                      </td>
                    );
                  })}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
};
