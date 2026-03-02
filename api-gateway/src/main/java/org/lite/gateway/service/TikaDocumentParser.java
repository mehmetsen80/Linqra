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
import org.apache.tika.sax.ToXMLContentHandler;
import org.zwobble.mammoth.DocumentConverter;
import org.zwobble.mammoth.Result;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTR;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for parsing documents using Apache Tika and Mammoth
 */
@Service
@Slf4j
public class TikaDocumentParser {

    private final Tika tika;
    private final AutoDetectParser parser;
    private final DocumentConverter mammothConverter;

    public TikaDocumentParser() {
        this.tika = new Tika();
        this.parser = new AutoDetectParser();
        this.mammothConverter = new DocumentConverter();
    }

    /**
     * Extract text content from a document
     * 
     * @param fileContent The file content as a byte array
     * @param contentType The MIME type of the file (e.g., "application/pdf")
     * @return Parsed text content
     */
    public ParseResult parse(byte[] fileContent, String contentType) {
        try {
            String html;
            Metadata metadata = new Metadata();
            metadata.set(Metadata.CONTENT_TYPE, contentType);

            // Use Mammoth for DOCX to get high-fidelity HTML (paragraphs, tables,
            // checkboxes)
            if (isDocx(contentType)) {
                log.info("Using Mammoth with POI pre-processing for DOCX to HTML conversion");
                byte[] processedContent = preprocessDocx(fileContent);
                try (InputStream is = new ByteArrayInputStream(processedContent)) {
                    Result<String> result = mammothConverter.convertToHtml(is);
                    html = result.getValue();
                    // Mammoth doesn't extract full metadata like Tika, so we still run Tika for
                    // metadata/page count
                    parser.parse(new ByteArrayInputStream(fileContent), new org.apache.tika.sax.BodyContentHandler(-1),
                            metadata, new ParseContext());
                }
            } else {
                // Use Tika for all other formats
                log.info("Using Tika for {} to HTML conversion", contentType);
                try (InputStream inputStream = new ByteArrayInputStream(fileContent)) {
                    java.io.ByteArrayOutputStream outputStream = new java.io.ByteArrayOutputStream();
                    ContentHandler handler = new ToXMLContentHandler(outputStream, "UTF-8");
                    ParseContext parseContext = new ParseContext();
                    parser.parse(inputStream, handler, metadata, parseContext);
                    html = outputStream.toString(StandardCharsets.UTF_8);
                }
            }

            // Inject styles and wrap content for better visual fidelity
            String css = "<meta charset=\"UTF-8\">\n<style>\n" +
                    "  .docx-content { font-family: 'Times New Roman', Times, serif; line-height: 1.5; color: #000; }\n"
                    +
                    "  .docx-content p { margin: 1em 0; min-height: 1em; }\n" +
                    "  .docx-content table { border-collapse: collapse; margin: 1em 0; border: 1px solid black; }\n" +
                    "  .docx-content th, .docx-content td { border: 1px solid black; padding: 8px; text-align: left; vertical-align: top; }\n"
                    +
                    "  .docx-content ol { list-style-type: lower-alpha; margin: 1em 0; padding-left: 2.5em; }\n" +
                    "  .docx-content ol ol { list-style-type: decimal; }\n" +
                    "  .docx-content ol ol ol { list-style-type: lower-roman; }\n" +
                    "  .docx-content ul { list-style-type: disc; margin: 1em 0; padding-left: 2.5em; }\n" +
                    "</style>\n";

            // IMPORTANT: extract plain text from the RAW Mammoth HTML — before &nbsp;
            // substitution and before the CSS/wrapper are added — so chunk text fed to
            // graph extraction is clean text with no HTML entities or CSS rules.
            String plainText = html.replaceAll("<[^>]*>", " ")
                    .replaceAll("\\s+", " ")
                    .trim();

            // Preserve multiple spaces and tabs using &nbsp; to prevent browser collapsing
            // We replace sequences of 2+ spaces or any tabs with their &nbsp; equivalent
            Pattern spacePattern = Pattern.compile(" {2,}|\\t");
            Matcher matcher = spacePattern.matcher(html);
            StringBuilder sb = new StringBuilder();
            int lastEnd = 0;
            while (matcher.find()) {
                sb.append(html, lastEnd, matcher.start());
                String match = matcher.group();
                if (match.equals("\t")) {
                    sb.append("&nbsp;&nbsp;&nbsp;&nbsp;"); // Standard tab replacement
                } else {
                    sb.append("&nbsp;".repeat(match.length()));
                }
                lastEnd = matcher.end();
            }
            sb.append(html, lastEnd, html.length());
            html = sb.toString();

            html = css + "<div class=\"docx-content\">\n" + html + "\n</div>";

            int pageCount = getPageCount(metadata);

            log.info("Successfully parsed document. HTML length: {}, Text length: {}, Pages: {}",
                    html.length(), plainText.length(), pageCount);

            List<ParseResult.FormField> formFields = extractPdfFormFields(fileContent, contentType);

            return ParseResult.builder()
                    .text(plainText)
                    .html(html)
                    .metadata(metadata)
                    .pageCount(pageCount)
                    .formFields(formFields.isEmpty() ? null : formFields)
                    .build();

        } catch (IOException | SAXException | TikaException e) {
            log.error("Error parsing document", e);
            throw new RuntimeException("Failed to parse document: " + e.getMessage(), e);
        }
    }

    private boolean isDocx(String contentType) {
        return contentType != null
                && (contentType.equals("application/vnd.openxmlformats-officedocument.wordprocessingml.document") ||
                        contentType.equals("application/x-tika-ooxml"));
    }

    /**
     * Detect content type from file content
     * 
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
    /**
     * Pre-process DOCX to handle elements Mammoth ignores (like legacy VML
     * checkboxes)
     */
    private byte[] preprocessDocx(byte[] content) {
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(content))) {
            boolean modified = false;

            // Process paragraphs
            for (XWPFParagraph p : doc.getParagraphs()) {
                modified |= processParagraph(p);
            }

            // Process tables
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        for (XWPFParagraph p : cell.getParagraphs()) {
                            modified |= processParagraph(p);
                        }
                    }
                }
            }

            if (modified) {
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                doc.write(out);
                return out.toByteArray();
            }
        } catch (Exception e) {
            log.warn("Failed to pre-process DOCX: {}", e.getMessage());
        }
        return content;
    }

    private boolean processParagraph(XWPFParagraph p) {
        boolean modified = false;
        List<XWPFRun> runs = p.getRuns();

        for (int i = 0; i < runs.size(); i++) {
            XWPFRun r = runs.get(i);
            CTR ctr = r.getCTR();
            String xml = ctr.xmlText();

            if (xml.contains("w:txbxContent")) {
                // Extract plain text from legacy VML textboxes which Mammoth usually ignores.
                log.info("Found legacy VML textbox, extracting text content");
                Pattern tPattern = Pattern.compile("<w:t[^>]*>([^<]*)</w:t>");
                Matcher tMatcher = tPattern.matcher(xml);
                StringBuilder txbxText = new StringBuilder();
                while (tMatcher.find()) {
                    String segment = tMatcher.group(1).trim();
                    if (!segment.isEmpty()) {
                        if (txbxText.length() > 0)
                            txbxText.append(" ");
                        txbxText.append(segment);
                    }
                }
                if (txbxText.length() > 0) {
                    XWPFRun newRun = p.insertNewRun(i);
                    newRun.setText(txbxText.toString());
                    modified = true;
                    i++;
                }
            }
        }

        return modified;
    }

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
        private String html;
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
