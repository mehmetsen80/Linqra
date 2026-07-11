import React from 'react';
import type { SlideData } from '../schemas';
import { ArchitectureSlide } from './ArchitectureSlide';
import { DataGridSlide } from './DataGridSlide';
import { TeamProfileSlide } from './TeamProfileSlide';
import { BentoBoxSlide } from './BentoBoxSlide';
import { CodeWalkthroughSlide } from './CodeWalkthroughSlide';
import { TitleSlide } from './TitleSlide';
import { MetricGridSlide } from './MetricGridSlide';
import { CustomHtmlSlide } from './CustomHtmlSlide';
import { StandardContentSlide } from './StandardContentSlide';
import { SplitMediaSlide } from './SplitMediaSlide';
import { ChartFocusSlide } from './ChartFocusSlide';
import { DiagramFocusSlide } from './DiagramFocusSlide';
import { TimelineSlide } from './TimelineSlide';
import { ProcessFlowSlide } from './ProcessFlowSlide';
import { CodeBlockSlide } from './CodeBlockSlide';
import { AlternatingFlowSlide } from './AlternatingFlowSlide';
import { SerpentineFlowSlide } from './SerpentineFlowSlide';
import { ChevronProcessSlide } from './ChevronProcessSlide';
import { ArrowTimelineSlide } from './ArrowTimelineSlide';
import { InterlockingTrianglesSlide } from './InterlockingTrianglesSlide';
import { NodeBranchTimelineSlide } from './NodeBranchTimelineSlide';
import { AlternatingRingFlowSlide } from './AlternatingRingFlowSlide';
import { HexagonTimelineSlide } from './HexagonTimelineSlide';
import { TextBoxProcessSlide } from './TextBoxProcessSlide';
import { ComparisonSlide } from './ComparisonSlide';
import { MediaColumnGridSlide } from './MediaColumnGridSlide';
import { PortraitHeroSlide } from './PortraitHeroSlide';
import { MediaAnnotatorSlide } from './MediaAnnotatorSlide';
import { FocalMetricSlide } from './FocalMetricSlide';
import { NumberedTimelineSlide } from './NumberedTimelineSlide';
import { CalloutBoxSlide } from './CalloutBoxSlide';
import { FeatureSplitSlide } from './FeatureSplitSlide';
import { TaskListSlide } from './TaskListSlide';
import { PricingTierSlide } from './PricingTierSlide';
import { TestimonialSlide } from './TestimonialSlide';
import { FeatureBannerSlide } from './FeatureBannerSlide';
import { ProblemSplitSlide } from './ProblemSplitSlide';
import { PainPointGridSlide } from './PainPointGridSlide';
import { ProblemStatementSlide } from './ProblemStatementSlide';
import { StatusQuoComparisonSlide } from './StatusQuoComparisonSlide';
import { HighlightCardSlide } from './HighlightCardSlide';
import { ProcessBreakdownSlide } from './ProcessBreakdownSlide';
import { HeroTitleSlide } from './HeroTitleSlide';
import { BrandLogoSlide } from './BrandLogoSlide';
import { BadgeGroupSlide } from './BadgeGroupSlide';
import { TextBlockSlide } from './TextBlockSlide';
import { FlexSlide } from './FlexSlide';
import { CardSlide } from './CardSlide';
import { GridSlide } from './GridSlide';
import { MetricRowSlide } from './MetricRowSlide';
import { DynamicIcon } from './DynamicIcon';
import { ImageSlide } from './ImageSlide';
import { BadgeSlide } from './BadgeSlide';
import { ChartSlide } from './ChartSlide';
import { StepperSlide } from './StepperSlide';
import { DividerSlide } from './DividerSlide';
import { AvatarSlide } from './AvatarSlide';
import { CodeSnippetSlide } from './CodeSnippetSlide';

import { ButtonSlide } from './ButtonSlide';
import { ProgressBarSlide } from './ProgressBarSlide';
import { StatSlide } from './StatSlide';
import { QRCodeSlide } from './QRCodeSlide';
import { AlertSlide } from './AlertSlide';
import { CheckboxSlide } from './CheckboxSlide';
import { RadioSlide } from './RadioSlide';
import { ListItemSlide } from './ListItemSlide';

export const SlideComponentRenderer: React.FC<{ slide: SlideData }> = ({ slide }) => {
  switch (slide.layout) {
    case 'title_slide':
      return <TitleSlide content={slide.content} />;
    case 'icon':
      return <DynamicIcon name={slide.content.name} size={slide.content.size} color={slide.content.color} containerStyle={slide.content.containerStyle} />;
    case 'image':
      return <ImageSlide content={slide.content} />;
    case 'badge':
      return <BadgeSlide content={slide.content} />;
    case 'atomic_chart':
      return <ChartSlide content={slide.content} />;
    case 'stepper':
      return <StepperSlide content={slide.content} />;
    case 'divider':
      return <DividerSlide content={slide.content} />;
    case 'avatar':
      return <AvatarSlide content={slide.content} />;
    case 'code_snippet':
      return <CodeSnippetSlide content={slide.content} />;
    case 'button':
      return <ButtonSlide content={slide.content} />;
    case 'progress_bar':
      return <ProgressBarSlide content={slide.content} />;
    case 'qr_code':
      return <QRCodeSlide content={slide.content} />;
    case 'stat':
      return <StatSlide content={slide.content} />;
    case 'alert':
      return <AlertSlide content={slide.content} />;
    case 'metric_row':
      return <MetricRowSlide content={slide.content} />;
    case 'metric_grid':
      return <MetricGridSlide content={slide.content} />;
    case 'standard':
      return <StandardContentSlide content={slide.content} />;
    case 'split_media':
      return <SplitMediaSlide content={slide.content} />;
    case 'chart':
      return <ChartFocusSlide content={slide.content} />;
    case 'diagram':
      return <DiagramFocusSlide content={slide.content} />;
    case 'timeline':
      return <TimelineSlide content={slide.content} />;
    case 'data_grid':
      return <DataGridSlide content={slide.content} />;
    case 'team_profile':
      return <TeamProfileSlide content={slide.content} />;
    case 'bento_box':
      return <BentoBoxSlide content={slide.content} />;
    case 'code_walkthrough':
      return <CodeWalkthroughSlide content={slide.content} />;
    case 'architecture':
      return <ArchitectureSlide content={slide.content} />;
    case 'process_flow':
      return <ProcessFlowSlide content={slide.content} />;
    case 'code_block':
      return <CodeBlockSlide content={slide.content} />;
    case 'text_box_process':
      return <TextBoxProcessSlide content={slide.content} />;
    case 'comparison':
      return <ComparisonSlide content={slide.content} />;
    case 'media_column_grid':
      return <MediaColumnGridSlide content={slide.content} />;
    case 'numbered_timeline':
      return <NumberedTimelineSlide content={slide.content as any} />;
    case 'portrait_hero':
      return <PortraitHeroSlide content={slide.content as any} />;
    case 'callout_box':
      return <CalloutBoxSlide content={slide.content as any} />;
    case 'feature_split':
      return <FeatureSplitSlide content={slide.content as any} />;
    case 'task_list':
      return <TaskListSlide content={slide.content as any} />;
    case 'pricing_tiers':
      return <PricingTierSlide content={slide.content as any} />;
    case 'testimonial':
      return <TestimonialSlide content={slide.content as any} />;
    case 'problem_split':
      return <ProblemSplitSlide content={slide.content as any} />;
    case 'pain_point_grid':
      return <PainPointGridSlide content={slide.content as any} />;
    case 'problem_statement':
      return <ProblemStatementSlide content={slide.content as any} />;
    case 'status_quo_comparison':
      return <StatusQuoComparisonSlide content={slide.content as any} />;
    case 'highlight_card':
      return <HighlightCardSlide content={slide.content as any} />;
    case 'hero_title':
      return <HeroTitleSlide content={slide.content as any} />;
    case 'brand_logo':
      return <BrandLogoSlide content={slide.content as any} />;
    case 'badge_group':
      return <BadgeGroupSlide content={slide.content as any} />;
    case 'text_block':
      return <TextBlockSlide content={slide.content as any} />;
    case 'process_breakdown':
      return <ProcessBreakdownSlide content={slide.content as any} />;
    case 'feature_banner':
      return <FeatureBannerSlide content={slide.content as any} />;
    case 'media_annotator':
      return <MediaAnnotatorSlide content={slide.content} />;
    case 'focal_metric':
      return <FocalMetricSlide content={slide.content} />;
    case 'node_branch_timeline':
      return <NodeBranchTimelineSlide content={slide.content} />;
    case 'custom_html':
      return <CustomHtmlSlide content={slide.content} />;
    case 'alternating_flow':
      return <AlternatingFlowSlide content={slide.content} />;
    case 'serpentine_flow':
      return <SerpentineFlowSlide content={slide.content} />;
    case 'chevron_process':
      return <ChevronProcessSlide content={slide.content} />;
    case 'arrow_timeline':
      return <ArrowTimelineSlide content={slide.content as any} />;
    case 'hexagon_timeline':
      return <HexagonTimelineSlide content={slide.content as any} />;
    case 'interlocking_triangles':
      return <InterlockingTrianglesSlide content={slide.content as any} />;
    case 'alternating_ring_flow':
      return <AlternatingRingFlowSlide content={slide.content as any} />;
    case 'flex':
      return <FlexSlide content={slide.content as any} />;
    case 'card':
      return <CardSlide content={slide.content as any} />;
    case 'grid':
      return <GridSlide content={slide.content as any} />;
    // ATOMIC COMPONENTS
    case 'qr_code':
      return <QRCodeSlide content={slide.content as any} />;
    case 'alert':
      return <AlertSlide content={slide.content as any} />;
    case 'checkbox':
      return <CheckboxSlide content={slide.content as any} />;
    case 'radio':
      return <RadioSlide content={slide.content as any} />;
    case 'list_item':
      return <ListItemSlide content={slide.content as any} />;
    default:
      return <div>Unknown layout: {slide.layout}</div>;
  }
};
