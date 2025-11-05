package org.lite.gateway.service;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import opennlp.tools.sentdetect.SentenceDetectorME;
import opennlp.tools.sentdetect.SentenceModel;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Service for chunking text based on different strategies using NLP
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ChunkingService {
    
    // Simple tokenizer - split by whitespace
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\\s+");
    
    private static final Object MODEL_LOCK = new Object();
    private static SentenceModel sentenceModel;
    
    /**
     * Get OpenNLP sentence detector model (loaded lazily)
     */
    private SentenceModel getSentenceModel() {
        if (sentenceModel == null) {
            synchronized (MODEL_LOCK) {
                if (sentenceModel == null) {
                    try {
                        InputStream modelStream = getClass().getClassLoader()
                                .getResourceAsStream("models/en-sent.bin");
                        if (modelStream != null) {
                            sentenceModel = new SentenceModel(modelStream);
                            log.info("Loaded OpenNLP sentence detection model");
                        } else {
                            log.warn("OpenNLP sentence model not found, using simple regex fallback");
                        }
                    } catch (Exception e) {
                        log.error("Failed to load OpenNLP sentence model", e);
                    }
                }
            }
        }
        return sentenceModel;
    }
    
    /**
     * Chunk text based on token count with overlap
     * @param text The text to chunk
     * @param maxTokens Maximum tokens per chunk
     * @param overlapTokens Number of overlapping tokens between chunks
     * @return List of text chunks
     */
    public List<ChunkResult> chunkByTokens(String text, int maxTokens, int overlapTokens) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        // Split text into tokens
        String[] tokens = TOKEN_PATTERN.split(text);
        
        if (tokens.length <= maxTokens) {
            // Text fits in one chunk
            return List.of(ChunkResult.builder()
                    .text(text)
                    .tokenCount(tokens.length)
                    .startPosition(0)
                    .endPosition(tokens.length)
                    .build());
        }
        
        List<ChunkResult> chunks = new ArrayList<>();
        int currentIndex = 0;
        int chunkIndex = 0;
        
        while (currentIndex < tokens.length) {
            int endIndex = Math.min(currentIndex + maxTokens, tokens.length);
            
            // Create chunk text from tokens
            StringBuilder chunkText = new StringBuilder();
            for (int i = currentIndex; i < endIndex; i++) {
                if (i > currentIndex) {
                    chunkText.append(" "); // Add space between tokens
                }
                chunkText.append(tokens[i]);
            }
            
            chunks.add(ChunkResult.builder()
                    .text(chunkText.toString())
                    .tokenCount(endIndex - currentIndex)
                    .startPosition(currentIndex)
                    .endPosition(endIndex)
                    .chunkIndex(chunkIndex++)
                    .build());
            
            // Move to next chunk with overlap
            currentIndex = endIndex - overlapTokens;
        }
        
        log.info("Chunked text into {} chunks (maxTokens: {}, overlap: {})", 
                chunks.size(), maxTokens, overlapTokens);
        
        return chunks;
    }
    
    /**
     * Chunk text by sentences using OpenNLP
     * @param text The text to chunk
     * @param maxTokens Maximum tokens per chunk
     * @return List of text chunks
     */
    public List<ChunkResult> chunkBySentences(String text, int maxTokens) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        // Try to use OpenNLP for sentence detection, fall back to regex if not available
        String[] sentences = detectSentences(text);
        
        List<ChunkResult> chunks = new ArrayList<>();
        int chunkIndex = 0;
        StringBuilder currentChunk = new StringBuilder();
        int currentTokenCount = 0;
        int startPosition = 0;

        for (String sentence : sentences) {
            String[] sentenceTokens = TOKEN_PATTERN.split(sentence);
            int sentenceTokenCount = sentenceTokens.length;

            if (currentTokenCount + sentenceTokenCount > maxTokens && !currentChunk.isEmpty()) {
                // Current chunk is full, save it
                chunks.add(ChunkResult.builder()
                        .text(currentChunk.toString().trim())
                        .tokenCount(currentTokenCount)
                        .startPosition(startPosition)
                        .endPosition(startPosition + currentTokenCount)
                        .chunkIndex(chunkIndex++)
                        .build());

                // Start new chunk with current sentence
                currentChunk = new StringBuilder(sentence);
                currentTokenCount = sentenceTokenCount;
                startPosition = startPosition + currentTokenCount;
            } else {
                // Add to current chunk
                if (!currentChunk.isEmpty()) {
                    currentChunk.append(" ");
                }
                currentChunk.append(sentence);
                currentTokenCount += sentenceTokenCount;
            }
        }
        
        // Add remaining chunk
        if (!currentChunk.isEmpty()) {
            chunks.add(ChunkResult.builder()
                    .text(currentChunk.toString().trim())
                    .tokenCount(currentTokenCount)
                    .startPosition(startPosition)
                    .endPosition(startPosition + currentTokenCount)
                    .chunkIndex(chunkIndex)
                    .build());
        }
        
        log.info("Chunked text by sentences into {} chunks", chunks.size());
        return chunks;
    }
    
    /**
     * Chunk text by paragraphs
     * @param text The text to chunk
     * @param maxTokens Maximum tokens per chunk
     * @return List of text chunks
     */
    public List<ChunkResult> chunkByParagraphs(String text, int maxTokens) {
        if (text == null || text.trim().isEmpty()) {
            return new ArrayList<>();
        }
        
        // Split by double newline or paragraph markers
        String[] paragraphs = text.split("\\n\\s*\\n+");
        
        List<ChunkResult> chunks = new ArrayList<>();
        int chunkIndex = 0;
        StringBuilder currentChunk = new StringBuilder();
        int currentTokenCount = 0;
        int startPosition = 0;

        for (String paragraph : paragraphs) {
            String[] paragraphTokens = TOKEN_PATTERN.split(paragraph);
            int paragraphTokenCount = paragraphTokens.length;

            if (currentTokenCount + paragraphTokenCount > maxTokens && !currentChunk.isEmpty()) {
                // Current chunk is full, save it
                chunks.add(ChunkResult.builder()
                        .text(currentChunk.toString().trim())
                        .tokenCount(currentTokenCount)
                        .startPosition(startPosition)
                        .endPosition(startPosition + currentTokenCount)
                        .chunkIndex(chunkIndex++)
                        .build());

                // Start new chunk with current paragraph
                currentChunk = new StringBuilder(paragraph);
                currentTokenCount = paragraphTokenCount;
                startPosition = startPosition + currentTokenCount;
            } else {
                // Add to current chunk
                if (!currentChunk.isEmpty()) {
                    currentChunk.append("\n\n");
                }
                currentChunk.append(paragraph);
                currentTokenCount += paragraphTokenCount;
            }
        }
        
        // Add remaining chunk
        if (!currentChunk.isEmpty()) {
            chunks.add(ChunkResult.builder()
                    .text(currentChunk.toString().trim())
                    .tokenCount(currentTokenCount)
                    .startPosition(startPosition)
                    .endPosition(startPosition + currentTokenCount)
                    .chunkIndex(chunkIndex)
                    .build());
        }
        
        log.info("Chunked text by paragraphs into {} chunks", chunks.size());
        return chunks;
    }
    
    /**
     * Detect sentences using OpenNLP or fall back to regex
     */
    private String[] detectSentences(String text) {
        SentenceModel model = getSentenceModel();
        
        if (model != null) {
            try {
                SentenceDetectorME detector = new SentenceDetectorME(model);
                String[] sentences = detector.sentDetect(text);
                log.debug("Detected {} sentences using OpenNLP", sentences.length);
                return sentences;
            } catch (Exception e) {
                log.warn("Failed to use OpenNLP sentence detector, falling back to regex", e);
            }
        }
        
        // Fallback to simple regex-based sentence splitting
        String[] sentences = text.split("(?<=[.!?])\\s+");
        log.debug("Detected {} sentences using regex fallback", sentences.length);
        return sentences;
    }
    
    @Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ChunkResult {
        private String text;
        private Integer tokenCount;
        private Integer startPosition;
        private Integer endPosition;
        private Integer chunkIndex;
        private java.util.List<Integer> pageNumbers;
    }
}
