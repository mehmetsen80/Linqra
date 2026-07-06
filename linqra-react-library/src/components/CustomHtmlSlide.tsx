import React from 'react';
import type { CustomHtmlSlideContent } from '../schemas';
import parse, { Element } from 'html-react-parser';
import { TitleSlide } from './TitleSlide';
import { MetricGridSlide } from './MetricGridSlide';
import { StandaloneChart } from './ChartFocusSlide';
import { StandardContentSlide } from './StandardContentSlide';
import { SplitMediaSlide } from './SplitMediaSlide';
import { StandaloneDiagram } from './DiagramFocusSlide';
import { CodeBlockSlide } from './CodeBlockSlide';
import { AlternatingRingFlowSlide } from './AlternatingRingFlowSlide';
import { AlternatingFlowSlide } from './AlternatingFlowSlide';
import { SerpentineFlowSlide } from './SerpentineFlowSlide';
import { ChevronProcessSlide } from './ChevronProcessSlide';
import { ArrowTimelineSlide } from './ArrowTimelineSlide';
import { InterlockingTrianglesSlide } from './InterlockingTrianglesSlide';
import { HexagonTimelineSlide } from './HexagonTimelineSlide';
import { ProcessFlowSlide } from './ProcessFlowSlide';
import { NodeBranchTimelineSlide } from './NodeBranchTimelineSlide';

interface Props {
  content: CustomHtmlSlideContent;
}

export const CustomHtmlSlide: React.FC<Props> = ({ content }) => {
  const options = {
    replace: (domNode: any) => {
      if (domNode instanceof Element && domNode.attribs) {
        if (domNode.name === 'deqra-title-slide') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <TitleSlide key={`title-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-title-slide", e);
            }
          }
        }
        if (domNode.name === 'deqra-metric-grid') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <MetricGridSlide key={`metric-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-metric-grid", e);
            }
          }
        }
        if (domNode.name === 'deqra-chart') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedSpec = JSON.parse(dataAttr);
              return <StandaloneChart key={`chart-${Math.random()}`} chartSpec={parsedSpec.chartSpec || parsedSpec} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-chart", e);
            }
          }
        }
        if (domNode.name === 'deqra-standard-content') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <StandardContentSlide key={`std-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-standard-content", e);
            }
          }
        }
        if (domNode.name === 'deqra-split-media') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <SplitMediaSlide key={`split-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-split-media", e);
            }
          }
        }
        if (domNode.name === 'deqra-diagram') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedSpec = JSON.parse(dataAttr);
              return <StandaloneDiagram key={`diag-${Math.random()}`} diagramSpec={parsedSpec.diagramSpec || parsedSpec} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-diagram", e);
            }
          }
        }
        if (domNode.name === 'deqra-code-block') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <CodeBlockSlide key={`code-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-code-block", e);
            }
          }
        }
        if (domNode.name === 'deqra-alternating-ring-flow') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <AlternatingRingFlowSlide key={`altring-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-alternating-ring-flow", e);
            }
          }
        }
        if (domNode.name === 'deqra-alternating-flow') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <AlternatingFlowSlide key={`altflow-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-alternating-flow", e);
            }
          }
        }
        if (domNode.name === 'deqra-serpentine-flow') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <SerpentineFlowSlide key={`serp-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-serpentine-flow", e);
            }
          }
        }
        if (domNode.name === 'deqra-chevron-process') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <ChevronProcessSlide key={`chev-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-chevron-process", e);
            }
          }
        }
        if (domNode.name === 'deqra-arrow-timeline') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <ArrowTimelineSlide key={`arrow-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-arrow-timeline", e);
            }
          }
        }
        if (domNode.name === 'deqra-interlocking-triangles') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <InterlockingTrianglesSlide key={`tri-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-interlocking-triangles", e);
            }
          }
        }
        if (domNode.name === 'deqra-hexagon-timeline') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <HexagonTimelineSlide key={`hextl-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-hexagon-timeline", e);
            }
          }
        }
        if (domNode.name === 'deqra-node-branch-timeline') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <NodeBranchTimelineSlide key={`nbrt-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-node-branch-timeline", e);
            }
          }
        }
        if (domNode.name === 'deqra-process-flow') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <ProcessFlowSlide key={`proc-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-process-flow", e);
            }
          }
        }
      }
    }
  };

  return (
    <div 
      className="custom-html-slide"
      style={{ width: '100%', height: '100%', boxSizing: 'border-box', overflow: 'visible' }}
    >
      {parse(content.html, options)}
    </div>
  );
};
