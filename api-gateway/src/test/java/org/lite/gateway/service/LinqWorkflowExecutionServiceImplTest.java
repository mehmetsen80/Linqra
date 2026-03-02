package service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class LinqWorkflowExecutionServiceImplTest {

    @Test
    void testStepResultPatternMatching() {
        // Given - The exact regex pattern from the code
        Pattern stepPattern = Pattern.compile("\\{\\{step(\\d+)\\.result(?:\\.([^}]+))?\\}\\}");
        
        // Test cases
        String[] testInputs = {
            "{{step1.result}}",
            "{{step3.result.choices[0].message.content}}",
            "{{step2.result.total_results}}",
            "{{step6.result.inventoryname}}",
            "{{step5.result.data.users[0].profile.name}}",
            "{{step99.result}}",
            "{{step1.result.}}",
            "Hello {{step1.result}} world",
            "{{step1.result}} and {{step2.result}}"
        };
        
        // When & Then
        for (String input : testInputs) {
            Matcher matcher = stepPattern.matcher(input);
            
            System.out.println("Testing: " + input);
            
            while (matcher.find()) {
                int stepNum = Integer.parseInt(matcher.group(1));
                String path = matcher.group(2);
                
                System.out.println("  Step: " + stepNum + ", Path: " + path);
                
                // Verify step number is parsed correctly
                assertTrue(stepNum > 0, "Step number should be positive");
                
                // Verify path extraction works for complex paths
                if (path != null) {
                    assertFalse(path.isEmpty(), "Path should not be empty if present");
                    System.out.println("  Extracted path: " + path);
                }
            }
        }
    }
    
    @Test
    void testStepResultPatternWithComplexPaths() {
        // Given
        Pattern stepPattern = Pattern.compile("\\{\\{step(\\d+)\\.result(?:\\.([^}]+))?\\}\\}");
        
        // Test complex JSON paths
        String complexPath = "{{step3.result.choices[0].message.content}}";
        Matcher matcher = stepPattern.matcher(complexPath);
        
        // When
        assertTrue(matcher.find(), "Should match complex path");
        
        int stepNum = Integer.parseInt(matcher.group(1));
        String path = matcher.group(2);
        
        // Then
        assertEquals(3, stepNum, "Step number should be 3");
        assertEquals("choices[0].message.content", path, "Should extract full complex path");
    }
    
    @Test
    void testStepResultPatternWithArrayAccess() {
        // Given
        Pattern stepPattern = Pattern.compile("\\{\\{step(\\d+)\\.result(?:\\.([^}]+))?\\}\\}");
        
        // Test array access
        String arrayPath = "{{step2.result.items[0].name}}";
        Matcher matcher = stepPattern.matcher(arrayPath);
        
        // When
        assertTrue(matcher.find(), "Should match array path");
        
        int stepNum = Integer.parseInt(matcher.group(1));
        String path = matcher.group(2);
        
        // Then
        assertEquals(2, stepNum, "Step number should be 2");
        assertEquals("items[0].name", path, "Should extract array path");
    }
    
    @Test
    void testStepResultPatternWithSimplePath() {
        // Given
        Pattern stepPattern = Pattern.compile("\\{\\{step(\\d+)\\.result(?:\\.([^}]+))?\\}\\}");
        
        // Test simple path
        String simplePath = "{{step1.result}}";
        Matcher matcher = stepPattern.matcher(simplePath);
        
        // When
        assertTrue(matcher.find(), "Should match simple path");
        
        int stepNum = Integer.parseInt(matcher.group(1));
        String path = matcher.group(2);
        
        // Then
        assertEquals(1, stepNum, "Step number should be 1");
        assertNull(path, "Path should be null for simple result");
    }
} 