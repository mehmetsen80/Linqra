import React, { useEffect, useState } from 'react';
import type { DiagramFocusSlideContent } from '../schemas';
import mermaid from 'mermaid';
import { renderText } from '../utils';

interface Props {
  content: DiagramFocusSlideContent;
}
export const StandaloneDiagram: React.FC<{ diagramSpec: DiagramFocusSlideContent['diagramSpec'] }> = ({ diagramSpec }) => {
  const [svg, setSvg] = useState<string>('');

  useEffect(() => {
    // We must pass explicit hex colors to clear any cached CSS variables 
    // from previous hot-reloads that might still be in Mermaid's global state.
    mermaid.initialize({ 
      startOnLoad: false, 
      theme: 'base',
      themeVariables: {
        primaryColor: '#ffffff',
        primaryTextColor: '#000000',
        primaryBorderColor: '#cccccc',
        lineColor: '#cccccc',
        textColor: '#000000',
        mainBkg: '#ffffff',
        nodeBorder: '#cccccc',
        clusterBkg: '#ffffff',
        clusterBorder: '#cccccc',
        defaultLinkColor: '#cccccc',
        labelBoxBkgColor: '#ffffff',
        labelBoxBorderColor: '#cccccc',
        labelTextColor: '#000000'
      }
    });

    let mermaidCode = '';
    
    if (diagramSpec.type === 'flowchart' || diagramSpec.type === 'network') {
      mermaidCode = 'graph TD;\n';
      diagramSpec.nodes.forEach(node => {
        mermaidCode += `  ${node.id}["${node.label}"]\n`;
      });
      diagramSpec.edges.forEach(edge => {
        let link = '-->';
        if (edge.arrowType === 'thick') link = '==>';
        else if (edge.arrowType === 'dotted') link = '-.->';
        else if (edge.arrowType === 'line') link = '---';
        else if (edge.arrowType === 'bidirectional') link = '<-->';
        
        if (edge.label) {
          mermaidCode += `  ${edge.source}${link}|"${edge.label}"|${edge.target}\n`;
        } else {
          mermaidCode += `  ${edge.source}${link}${edge.target}\n`;
        }
      });
    } else if (diagramSpec.type === 'sequence') {
      mermaidCode = 'sequenceDiagram\n';
      diagramSpec.nodes.forEach(node => {
        mermaidCode += `  participant ${node.id} as ${node.label}\n`;
      });
      diagramSpec.edges.forEach(edge => {
        let link = '->>';
        if (edge.arrowType === 'dotted') link = '-->>';
        else if (edge.arrowType === 'line') link = '->';
        
        const text = edge.label || 'Message';
        mermaidCode += `  ${edge.source}${link}${edge.target}: ${text}\n`;
      });
    }

    const renderDiagram = async () => {
      try {
        const id = `mermaid-${Math.random().toString(36).substr(2, 9)}`;
        const { svg: svgResult } = await mermaid.render(id, mermaidCode);
        // Mermaid bakes in ID-scoped styles like "#mermaid-xxx .edgeLabel rect { opacity: 0.5 }"
        // which have specificity (1,1,1) and beat any external class-only overrides.
        // The only reliable fix: inject our own overrides using the SAME id we generated,
        // matching Mermaid's specificity exactly — then cascade order (last rule wins) takes over.
        const labelBg      = diagramSpec.edgeLabelStyle?.background   ?? 'rgba(255,255,255,0.85)';
        const labelColor   = diagramSpec.edgeLabelStyle?.color        ?? '#333';
        const labelRadius  = diagramSpec.edgeLabelStyle?.borderRadius ?? '8px';
        const labelPadding = diagramSpec.edgeLabelStyle?.padding      ?? '1px 6px';
        const overrideStyle = `<style>
          #${id} foreignObject { overflow: visible !important; }
          #${id} p { margin: 0; background-color: transparent; }
          #${id} .edgeLabel { background-color: transparent; text-align: center; overflow: visible !important; }
          #${id} .edgeLabel p { color: ${labelColor}; background-color: ${labelBg}; padding: ${labelPadding}; border-radius: ${labelRadius}; display: inline-block; font-size: 10px; line-height: 1.4; white-space: nowrap; }
          #${id} .edgeLabel rect { opacity: 0 !important; }
          #${id} .labelBkg { display: none; }
        </style>`;
        const finalSvg = svgResult.replace('</svg>', overrideStyle + '</svg>');
        setSvg(finalSvg);
      } catch (err) {
        console.error("Mermaid parsing error", err);
        setSvg(`<div style="color:red">Failed to render diagram</div>`);
      }
    };

    renderDiagram();
  }, [diagramSpec]);

  return (
    <div className="mermaid-wrapper" style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'hidden', position: 'relative' }}>
      <style>{`
        .mermaid-wrapper svg {
          max-width: 100% !important;
          max-height: 100% !important;
          height: auto !important;
        }
        /* Critical base styles Mermaid relied on in its now-stripped style block */
        .mermaid-wrapper svg {
          font-family: "trebuchet ms", verdana, arial, sans-serif;
          font-size: 16px;
        }
        .mermaid-wrapper p {
          margin: 0;
          background-color: transparent;
        }
        .mermaid-wrapper .label {
          color: var(--diagram-node-text);
          text-align: center;
        }
        /* Dynamic Theming Overrides */
        .mermaid-wrapper .node rect,
        .mermaid-wrapper .node circle,
        .mermaid-wrapper .node ellipse,
        .mermaid-wrapper .node polygon,
        .mermaid-wrapper .node path {
          fill: var(--diagram-node-bg) !important;
          stroke: var(--diagram-node-border) !important;
          stroke-width: 2px !important;
          rx: var(--diagram-node-radius) !important;
        }
        .mermaid-wrapper .node .label,
        .mermaid-wrapper .node span {
          color: var(--diagram-node-text) !important;
          fill: var(--diagram-node-text) !important;
        }
        .mermaid-wrapper .edgePath path,
        .mermaid-wrapper .flowchart-link {
          stroke: var(--diagram-edge) !important;
          stroke-width: 2px !important;
          fill: none !important;
        }
        .mermaid-wrapper marker path,
        .mermaid-wrapper .arrowheadPath {
          fill: var(--diagram-edge) !important;
          stroke: var(--diagram-edge) !important;
        }
        /* Edge Label Overrides */
        .mermaid-wrapper .edgeLabel {
          background-color: var(--diagram-label-bg) !important;
          padding: 4px 12px !important;
          border-radius: 20px !important;
          border: 1px solid var(--card-border) !important;
        }
        .mermaid-wrapper .edgeLabel span,
        .mermaid-wrapper .edgeLabel text {
          color: var(--text-secondary) !important;
          fill: var(--text-secondary) !important;
        }
        .mermaid-wrapper .edgeLabel rect {
          fill: var(--diagram-label-bg) !important;
          stroke: var(--card-border) !important;
        }
      `}</style>
      <div dangerouslySetInnerHTML={{ __html: svg }} style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', minWidth: 0, minHeight: 0 }} />
    </div>
  );
};

export const DiagramFocusSlide: React.FC<Props> = ({ content }) => {
  const { diagramSpec } = content;

  return (
    <div style={{ containerType: 'size', padding: 'clamp(1rem, 5cqmin, 3rem) clamp(1rem, 6cqmin, 4rem)', height: '100%', display: 'flex', flexDirection: 'column', boxSizing: 'border-box' }}>
      {renderText(content.title, { marginBottom: 'clamp(1rem, 3cqmin, 2rem)', color: 'var(--text-main)', fontSize: 'clamp(2rem, 6cqmin, 3rem)' }, 'h1')}
      <div style={{ flex: 1, minHeight: 0, minWidth: 0, background: 'var(--card-bg)', padding: 'clamp(0.5rem, 2cqmin, 2rem)', borderRadius: '12px', border: '1px solid var(--card-border)', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
        <StandaloneDiagram diagramSpec={diagramSpec} />
      </div>
    </div>
  );
};
