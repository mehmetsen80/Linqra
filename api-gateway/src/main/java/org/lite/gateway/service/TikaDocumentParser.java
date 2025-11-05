package org.lite.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Service for parsing documents using Apache Tika
 */
@Service
@Slf4j
public class TikaDocumentParser {
    
    private final Tika tika;
    private final AutoDetectParser parser;
    
    public TikaDocumentParser() {
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
    }
    
    /**
     * Extract text content from a document
     * @param fileContent The file content as a byte array
     * @param contentType The MIME type of the file (e.g., "application/pdf")
     * @return Parsed text content
     */
    public ParseResult parse(byte[] fileContent, String contentType) {
        try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, contentType);
            
            // Use BodyContentHandler with a maximum limit of 1MB
            BodyContentHandler handler = new BodyContentHandler(10 * 1024 * 1024);
            ParseContext parseContext = new ParseContext();
            
            parser.parse(inputStream, handler, metadata, parseContext);
            
            String text = handler.toString();
            int pageCount = getPageCount(metadata);
            
            log.info("Successfully parsed document. Text length: {}, Pages: {}", 
                    text.length(), pageCount);
            
            return ParseResult.builder()
                    .text(text)
                    .metadata(metadata)
                    .pageCount(pageCount)
                    .build();
                    
        } catch (IOException | SAXException | TikaException e) {
            log.error("Error parsing document with Tika", e);
            throw new RuntimeException("Failed to parse document: " + e.getMessage(), e);
        }
    }
    
    /**
     * Detect content type from file content
     * @param fileContent The file content as a byte array
     * @return Detected MIME type
     */
    public String detectContentType(byte[] fileContent) {
        try {
            return tika.detect(fileContent);
        } catch (Exception e) {
            log.error("Error detecting content type", e);
            return "application/octet-stream";
        }
    }
    
    /**
     * Extract page count from Tika metadata
     */
    private int getPageCount(Metadata metadata) {
        String pageCountStr = metadata.get("xmpTPg:NPages");
        if (pageCountStr != null) {
            try {
                return Integer.parseInt(pageCountStr);
            } catch (NumberFormatException e) {
                log.warn("Could not parse page count: {}", pageCountStr);
            }
        }
        return 0;
    }
    
    /**
     * Result of document parsing
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ParseResult {
        private String text;
        private Metadata metadata;
        private int pageCount;
    }
}

