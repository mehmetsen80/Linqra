package service;

import org.junit.jupiter.api.Test;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests to verify the keyword extraction logic used in Hybrid Search.
 */
class RegexTest {

    @Test
    void testStandardExtraction() {
        String query = "What is the status of ABR-4944 and Part 7?";
        Set<String> keywords = extractKeywords(query);

        System.out.println("Query: " + query);
        System.out.println("Extracted: " + keywords);

        assertTrue(keywords.contains("ABR-4944"), "Should extract ID 'ABR-4944'");
        assertTrue(keywords.contains("Part 7"), "Should extract capitalized phrase 'Part 7'");
    }

    @Test
    void testMultiWordTitle() {
        String query = "Checking the Visa Application Status for my wife";
        Set<String> keywords = extractKeywords(query);

        System.out.println("Query: " + query);
        System.out.println("Extracted: " + keywords);

        assertTrue(keywords.contains("Visa Application Status"), "Should extract 'Visa Application Status'");
    }

    @Test
    void testQuotedText() {
        String query = "Tell me about \"Biographic Information\" section";
        Set<String> keywords = extractKeywords(query);

        System.out.println("Query: " + query);
        System.out.println("Extracted: " + keywords);

        assertTrue(keywords.contains("Biographic Information"), "Should extract quoted text 'Biographic Information'");
    }

    @Test
    void testGeneralInstructions() {
        String query = "Where are the General Instructions located?";
        Set<String> keywords = extractKeywords(query);

        System.out.println("Query: " + query);
        System.out.println("Extracted: " + keywords);

        assertTrue(keywords.contains("General Instructions"), "Should extract 'General Instructions'");
    }

    @Test
    void testFormIdentifiers() {
        String query = "I need help with I-485 and N-400 forms";
        Set<String> keywords = extractKeywords(query);

        System.out.println("Query: " + query);
        System.out.println("Extracted: " + keywords);

        assertTrue(keywords.contains("I-485"), "Should extract 'I-485'");
        assertTrue(keywords.contains("N-400"), "Should extract 'N-400'");
    }

    @Test
    void testMixedComplexQuery() {
        String query = "Does \"Part 3\" of Form I-130 cover Visa Application Status?";
        Set<String> keywords = extractKeywords(query);

        System.out.println("Query: " + query);
        System.out.println("Extracted: " + keywords);

        assertTrue(keywords.contains("Part 3"), "Should extract quoted 'Part 3'");
        assertTrue(keywords.contains("I-130"), "Should extract 'I-130'");
        assertTrue(keywords.contains("Visa Application Status"), "Should extract 'Visa Application Status'");
    }

    // --- The Logic being tested (Copied from LinqMilvusStoreServiceImpl) ---

    private Set<String> extractKeywords(String query) {
        if (query == null || query.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> keywords = new HashSet<>();

        // 1. Extract Quoted Text
        Matcher quotedMatcher = Pattern.compile("\"([^\"]+)\"").matcher(query);
        while (quotedMatcher.find()) {
            keywords.add(quotedMatcher.group(1));
        }

        // 2. Extract Capitalized Phrases (sequences of Capitalized Words, excluding
        // common stop words if needed, but keeping simple for now)
        // Improved Regex: Allows subsequent words to be numbers (e.g. "Part 7")
        // Pattern: \b[A-Z][a-zA-Z0-9]*(?:\s+([A-Z][a-zA-Z0-9]*|[0-9]+))+\b
        Matcher capitalizedMatcher = Pattern.compile("\\b[A-Z][a-zA-Z0-9]*(?:\\s+(?:[A-Z][a-zA-Z0-9]*|[0-9]+))+\\b")
                .matcher(query);
        while (capitalizedMatcher.find()) {
            String phrase = capitalizedMatcher.group();
            // Filter out single short words (like "The", "A") unless they are part of a
            // phrase or look like an ID
            // The regex enforces at least 2 tokens, so single words are already excluded.
            keywords.add(phrase);
        }

        // 3. Extract Alphanumeric Identifiers (e.g. I-485, N-400) - specifically
        // looking for letter-number patterns
        // \b[A-Za-z]+-\d+\b
        Matcher idMatcher = Pattern.compile("\\b[A-Za-z]+-\\d+\\b").matcher(query);
        while (idMatcher.find()) {
            keywords.add(idMatcher.group());
        }

        return keywords;
    }
}
