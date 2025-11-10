package org.lite.gateway.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationWidget;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDCheckBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDComboBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDListBox;
import org.apache.pdfbox.pdmodel.interactive.form.PDRadioButton;
import org.apache.pdfbox.pdmodel.interactive.form.PDNonTerminalField;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.xml.sax.SAXException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            
            List<ParseResult.FormField> formFields = extractPdfFormFields(fileContent, contentType);
            
            return ParseResult.builder()
                    .text(text)
                    .metadata(metadata)
                    .pageCount(pageCount)
                    .formFields(formFields.isEmpty() ? null : formFields)
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
    
    private List<ParseResult.FormField> extractPdfFormFields(byte[] fileContent, String contentType) {
        if (fileContent == null || fileContent.length == 0) {
            return Collections.emptyList();
        }
        String normalizedType = contentType != null ? contentType.toLowerCase() : "";
        if (!normalizedType.contains("pdf")) {
            return Collections.emptyList();
        }
        
        try (PDDocument document = Loader.loadPDF(fileContent)) {
            PDAcroForm acroForm = Optional.ofNullable(document.getDocumentCatalog().getAcroForm())
                    .orElse(null);
            if (acroForm == null) {
                log.debug("PDF has no AcroForm fields");
                return Collections.emptyList();
            }
            
            Map<PDPage, Integer> pageIndexMap = buildPageIndexMap(document);
            List<ParseResult.FormField> fields = new ArrayList<>();
            
            for (PDField field : acroForm.getFieldTree()) {
                ParseResult.FormField formField = mapToFormField(field, pageIndexMap);
                if (formField != null) {
                    fields.add(formField);
                }
            }
            
            log.info("Extracted {} form fields from PDF", fields.size());
            return fields;
        } catch (IOException e) {
            log.warn("Failed to extract PDF form fields: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
    
    private Map<PDPage, Integer> buildPageIndexMap(PDDocument document) {
        Map<PDPage, Integer> map = new IdentityHashMap<>();
        int index = 1;
        for (PDPage page : document.getPages()) {
            map.put(page, index++);
        }
        return map;
    }
    
    private ParseResult.FormField mapToFormField(PDField field, Map<PDPage, Integer> pageIndexMap) {
        if (field == null) {
            return null;
        }

        if (field instanceof PDNonTerminalField) {
            // Skip parent/structural nodes; only capture actual input widgets
            return null;
        }
        
        String name = Optional.ofNullable(field.getAlternateFieldName())
                .filter(StringUtils::hasText)
                .orElse(field.getFullyQualifiedName());
        String type = resolveFieldType(field);
        String value = StringUtils.hasText(field.getValueAsString()) ? field.getValueAsString() : null;
        List<String> options = extractFieldOptions(field);
        Integer pageNumber = resolvePageNumber(field, pageIndexMap);
        
        return ParseResult.FormField.builder()
                .name(name)
                .type(type)
                .value(value)
                .options(options == null || options.isEmpty() ? null : options)
                .pageNumber(pageNumber)
                .required(field.isRequired())
                .build();
    }
    
    private String resolveFieldType(PDField field) {
        if (field instanceof PDTextField) {
            return "text";
        }
        if (field instanceof PDCheckBox) {
            return "checkbox";
        }
        if (field instanceof PDRadioButton) {
            return "radio";
        }
        if (field instanceof PDComboBox) {
            return "dropdown";
        }
        if (field instanceof PDListBox) {
            return "listbox";
        }
        if (field instanceof PDSignatureField) {
            return "signature";
        }
        return Optional.ofNullable(field.getFieldType()).orElse("unknown");
    }
    
    private List<String> extractFieldOptions(PDField field) {
        if (field instanceof PDComboBox comboBox) {
            return comboBox.getOptionsDisplayValues();
        }
        if (field instanceof PDListBox listBox) {
            return listBox.getOptionsDisplayValues();
        }
        return Collections.emptyList();
    }
    
    private Integer resolvePageNumber(PDField field, Map<PDPage, Integer> pageIndexMap) {
        try {
            List<PDAnnotationWidget> widgets = field.getWidgets();
            if (widgets == null || widgets.isEmpty()) {
                return null;
            }
            PDPage page = widgets.get(0).getPage();
            if (page == null) {
                return null;
            }
            return pageIndexMap.get(page);
        } catch (Exception e) {
            log.debug("Unable to resolve page number for field {}: {}", field.getFullyQualifiedName(), e.getMessage());
            return null;
        }
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
        private List<FormField> formFields;
        
        @lombok.Data
        @lombok.Builder
        @lombok.NoArgsConstructor
        @lombok.AllArgsConstructor
        public static class FormField {
            private String name;
            private String type;
            private String value;
            private List<String> options;
            private Integer pageNumber;
            private Boolean required;
        }
    }
}

