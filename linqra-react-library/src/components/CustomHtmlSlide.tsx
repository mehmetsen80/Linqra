import React from 'react';
import type { CustomHtmlSlideContent } from '../schemas';
import parse, { Element, domToReact } from 'html-react-parser';
import type { DOMNode } from 'html-react-parser';
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
import { TextBoxProcessSlide } from './TextBoxProcessSlide';
import { ComparisonSlide } from './ComparisonSlide';
import { MediaColumnGridSlide } from './MediaColumnGridSlide';
import { PortraitHeroSlide } from './PortraitHeroSlide';
import { MediaAnnotatorSlide } from './MediaAnnotatorSlide';
import { FocalMetricSlide } from './FocalMetricSlide';
import { FeatureSplitSlide } from './FeatureSplitSlide';
import { TaskListSlide } from './TaskListSlide';
import { FeatureBannerSlide } from './FeatureBannerSlide';
import { PricingTierSlide } from './PricingTierSlide';
import { FeatureCardSlide } from './FeatureCardSlide';
import { MetricRowSlide } from './MetricRowSlide';
import { OrbitDiagramSlide } from './OrbitDiagramSlide';
import { TestimonialSlide } from './TestimonialSlide';
import { ArchitectureSlide } from './ArchitectureSlide';
import { TimelineSlide } from './TimelineSlide';
import { NumberedTimelineSlide } from './NumberedTimelineSlide';
import { DataGridSlide } from './DataGridSlide';
import { TeamProfileSlide } from './TeamProfileSlide';
import { BentoBoxSlide } from './BentoBoxSlide';
import { CodeWalkthroughSlide } from './CodeWalkthroughSlide';
import { HighlightCardSlide } from './HighlightCardSlide';
import { HeroTitleSlide } from './HeroTitleSlide';
import { BrandLogoSlide } from './BrandLogoSlide';
import { BadgeGroupSlide } from './BadgeGroupSlide';
import { TextBlockSlide } from './TextBlockSlide';
import { CardSlide } from './CardSlide';
import { ListSlide } from './ListSlide';
import { GridSlide } from './GridSlide';
import { IconSlide } from './IconSlide';
import { ImageSlide } from './ImageSlide';
import { FlexSlide } from './FlexSlide';
import { DividerSlide } from './DividerSlide';
import { QuoteSlide } from './QuoteSlide';
import { AvatarSlide } from './AvatarSlide';
import { ButtonSlide } from './ButtonSlide';
import { StepSlide } from './StepSlide';
import { ConnectorSlide } from './ConnectorSlide';

import { CalloutBoxSlide } from './CalloutBoxSlide';
import { ProblemSplitSlide } from './ProblemSplitSlide';
import { PainPointGridSlide } from './PainPointGridSlide';
import { ProblemStatementSlide } from './ProblemStatementSlide';
import { StatusQuoComparisonSlide } from './StatusQuoComparisonSlide';
import { ProcessBreakdownSlide } from './ProcessBreakdownSlide';
import { QuadrantMatrixSlide } from './QuadrantMatrixSlide';
import { DetailedComparisonSlide } from './DetailedComparisonSlide';

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
        if (domNode.name === 'deqra-highlight-card') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <HighlightCardSlide key={`highlight-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-highlight-card", e);
            }
          }
        }
        if (domNode.name === 'deqra-hero-title') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <HeroTitleSlide key={`hero-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-hero-title", e);
            }
          }
        }
        if (domNode.name === 'deqra-brand-logo') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <BrandLogoSlide key={`logo-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-brand-logo", e);
            }
          }
        }
        if (domNode.name === 'deqra-badge-group') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <BadgeGroupSlide key={`badges-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-badge-group", e);
            }
          }
        }
        if (domNode.name === 'deqra-text-block') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <TextBlockSlide key={`text-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-text-block", e);
            }
          }
        }
        if (domNode.name === 'deqra-card') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              // Filter out the data attribute from being passed as a standard prop
              const { data: _, ...restAttribs } = domNode.attribs;
              return <CardSlide content={data} {...restAttribs}>{domToReact(domNode.children as DOMNode[], options)}</CardSlide>;
            } catch (e) {
              console.error("Failed to parse data for deqra-card", e);
            }
          }
        }
        if (domNode.name === 'deqra-feature-card') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <FeatureCardSlide key={`feature-card-${Math.random()}`} content={data} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-feature-card", e);
            }
          }
        }
        if (domNode.name === 'deqra-metric-row') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <MetricRowSlide key={`metric-row-${Math.random()}`} content={data} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-metric-row", e);
            }
          }
        }
        if (domNode.name === 'deqra-orbit-diagram') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <OrbitDiagramSlide key={`orbit-diagram-${Math.random()}`} content={data} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-orbit-diagram", e);
            }
          }
        }
        if (domNode.name === 'deqra-grid') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return (
                <GridSlide key={`grid-${Math.random()}`} content={parsedContent}>
                  {domToReact(domNode.children as DOMNode[], options)}
                </GridSlide>
              );
            } catch (e) {
              console.error("Failed to parse data for deqra-grid", e);
            }
          }
        }
        if (domNode.name === 'deqra-list') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <ListSlide key={`list-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-list", e);
            }
          }
        }
        if (domNode.name === 'deqra-icon') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <IconSlide key={`icon-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-icon", e);
            }
          }
        }
        if (domNode.name === 'deqra-image') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <ImageSlide key={`image-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-image", e);
            }
          }
        }
        if (domNode.name === 'deqra-flex') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return (
                <FlexSlide key={`flex-${Math.random()}`} content={parsedContent}>
                  {domToReact(domNode.children as DOMNode[], options)}
                </FlexSlide>
              );
            } catch (e) {
              console.error("Failed to parse data for deqra-flex", e);
            }
          }
        }
        if (domNode.name === 'deqra-divider') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <DividerSlide key={`divider-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-divider", e);
            }
          }
        }
        if (domNode.name === 'deqra-quote') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <QuoteSlide key={`quote-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-quote", e);
            }
          }
        }
        if (domNode.name === 'deqra-avatar') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <AvatarSlide key={`avatar-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-avatar", e);
            }
          }
        }
        if (domNode.name === 'deqra-button') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <ButtonSlide key={`button-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-button", e);
            }
          }
        }
        if (domNode.name === 'deqra-step') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <StepSlide key={`step-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-step", e);
            }
          }
        }
        if (domNode.name === 'deqra-connector') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <ConnectorSlide key={`connector-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-connector", e);
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
        if (domNode.name === 'deqra-text-box-process') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <TextBoxProcessSlide key={`tbprocess-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-text-box-process", e);
            }
          }
        }
        if (domNode.name === 'deqra-comparison') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <ComparisonSlide key={`comp-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-comparison", e);
            }
          }
        }
        if (domNode.name === 'deqra-media-column-grid') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <MediaColumnGridSlide key={`mcg-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-media-column-grid", e);
            }
          }
        }
        if (domNode.name === 'deqra-portrait-hero') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <PortraitHeroSlide key={`hero-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-portrait-hero", e);
            }
          }
        }
        if (domNode.name === 'deqra-media-annotator') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <MediaAnnotatorSlide key={`annotator-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-media-annotator", e);
            }
          }
        }
        if (domNode.name === 'deqra-focal-metric') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <FocalMetricSlide key={`focal-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-focal-metric", e);
            }
          }
        }
        if (domNode.name === 'deqra-feature-split') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const parsedContent = JSON.parse(dataAttr);
              return <FeatureSplitSlide key={`feature-${Math.random()}`} content={parsedContent} />;
            } catch (e) {
              console.error("Failed to parse data for deqra-feature-split", e);
            }
          }
        }
        if (domNode.name === 'deqra-task-list') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <TaskListSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-task-list data', e);
            }
          }
        }
        if (domNode.name === 'deqra-feature-banner') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <FeatureBannerSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-feature-banner data', e);
            }
          }
        }
        if (domNode.name === 'deqra-pricing-tier') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <PricingTierSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-pricing-tier data', e);
            }
          }
        }
        if (domNode.name === 'deqra-testimonial') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <TestimonialSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-testimonial data', e);
            }
          }
        }
        if (domNode.name === 'deqra-architecture') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <ArchitectureSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-architecture data', e);
            }
          }
        }
        if (domNode.name === 'deqra-timeline') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <TimelineSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-timeline data', e);
            }
          }
        }
        if (domNode.name === 'deqra-numbered-timeline') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <NumberedTimelineSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-numbered-timeline data', e);
            }
          }
        }
        if (domNode.name === 'deqra-data-grid') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <DataGridSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-data-grid data', e);
            }
          }
        }
        if (domNode.name === 'deqra-team-profile') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <TeamProfileSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-team-profile data', e);
            }
          }
        }
        if (domNode.name === 'deqra-bento-box') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <BentoBoxSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-bento-box data', e);
            }
          }
        }
        if (domNode.name === 'deqra-code-walkthrough') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <CodeWalkthroughSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-code-walkthrough data', e);
            }
          }
        }
        if (domNode.name === 'deqra-callout-box') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <CalloutBoxSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-callout-box data', e);
            }
          }
        }
        if (domNode.name === 'deqra-problem-split') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <ProblemSplitSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-problem-split data', e);
            }
          }
        }
        if (domNode.name === 'deqra-pain-point-grid') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <PainPointGridSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-pain-point-grid data', e);
            }
          }
        }
        if (domNode.name === 'deqra-problem-statement') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <ProblemStatementSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-problem-statement data', e);
            }
          }
        }
        if (domNode.name === 'deqra-status-quo-comparison') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <StatusQuoComparisonSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-status-quo-comparison data', e);
            }
          }
        }
        if (domNode.name === 'deqra-process-breakdown') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <ProcessBreakdownSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-process-breakdown data', e);
            }
          }
        }
        if (domNode.name === 'deqra-quadrant-matrix') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <QuadrantMatrixSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-quadrant-matrix data', e);
            }
          }
        }
        if (domNode.name === 'deqra-detailed-comparison') {
          const dataAttr = domNode.attribs['data'];
          if (dataAttr) {
            try {
              const data = JSON.parse(dataAttr);
              return <DetailedComparisonSlide content={data} />;
            } catch (e) {
              console.error('Failed to parse deqra-detailed-comparison data', e);
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
