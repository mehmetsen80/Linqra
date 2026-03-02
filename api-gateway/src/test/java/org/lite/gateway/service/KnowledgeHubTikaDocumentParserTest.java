package org.lite.gateway.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
public class KnowledgeHubTikaDocumentParserTest {

    private final TikaDocumentParser parser = new TikaDocumentParser();

    @Test
    public void testParseDocxToHtml() throws Exception {
        // Load the sample DOCX file
        ClassPathResource resource = new ClassPathResource("B1_EMPLOYMENT_CONTRACT.docx");
        assertTrue(resource.exists(), "Test file B1_EMPLOYMENT_CONTRACT.docx must exist in src/test/resources");

        byte[] fileContent;
        try (InputStream is = resource.getInputStream()) {
            fileContent = StreamUtils.copyToByteArray(is);
        }

        // Parse the document
        TikaDocumentParser.ParseResult result = parser.parse(fileContent,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        // Verify the results
        assertNotNull(result, "ParseResult should not be null");
        assertNotNull(result.getHtml(), "HTML content should not be null");
        assertNotNull(result.getText(), "Text content should not be null");

        // Check if HTML contains structural elements
        String html = result.getHtml();
        assertTrue(html.contains("<table") || html.contains("<tr"),
                "HTML should contain table tags if the document has tables");
        assertTrue(html.contains("<p"), "HTML should contain paragraph tags");

        // Specific content verification for B1_EMPLOYMENT_CONTRACT.docx
        assertTrue(html.contains("CONTRACT") || html.contains("Contract"), "HTML should contain contract keywords");

        // Verify plain text exists and is stripped of tags
        String text = result.getText();
        assertFalse(text.contains("<"), "Plain text should not contain HTML tags");
        assertFalse(text.isEmpty(), "Plain text should not be empty");

        // Verify page count
        assertTrue(result.getPageCount() >= 0, "Page count should be non-negative");

        System.out.println("HTML Sample: " + html.substring(0, Math.min(html.length(), 500)));
    }

    @Test
    public void testConvertAndSaveHtmlToResources() throws Exception {
        // Load the sample DOCX file
        ClassPathResource resource = new ClassPathResource("B1_EMPLOYMENT_CONTRACT.docx");
        assertTrue(resource.exists(), "Test file B1_EMPLOYMENT_CONTRACT.docx must exist in src/test/resources");

        byte[] fileContent;
        try (InputStream is = resource.getInputStream()) {
            fileContent = StreamUtils.copyToByteArray(is);
        }

        // Parse the document
        TikaDocumentParser.ParseResult result = parser.parse(fileContent,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

        assertNotNull(result.getHtml(), "HTML content should not be null");

        // Define the output path in src/test/resources
        // Note: This relies on the test being run from the project root or IDE knowing
        // the project structure
        String outputPath = "src/test/resources/B1_EMPLOYMENT_CONTRACT.html";

        // In Gradle/Maven environments, we often need to find the actual source
        // directory
        // For a simple unit test, we can try to save it relative to the project root
        File outputFile = new File(outputPath);

        // If the path doesn't exist (e.g. running from a different CWD), try to find it
        if (!outputFile.getParentFile().exists()) {
            // Fallback: use the absolute path of the resource if possible
            File resourceFile = resource.getFile();
            outputFile = new File(resourceFile.getParentFile(), "B1_EMPLOYMENT_CONTRACT.html");
        }

        Files.write(outputFile.toPath(), result.getHtml().getBytes(StandardCharsets.UTF_8));

        System.out.println("✅ Converted HTML saved to: " + outputFile.getAbsolutePath());
        assertTrue(outputFile.exists(), "Output HTML file should exist after writing");
    }
}
