package org.lite.gateway.enums;

/**
 * Categories for knowledge hub collections
 */
public enum KnowledgeCategory {
    // Technical Documentation
    TECHNICAL_DOCS("Technical Documentation", "API docs, SDKs, system architecture"),
    CODE_REFERENCE("Code Reference", "Code snippets, templates, examples"),
    TROUBLESHOOTING("Troubleshooting Guides", "Debug guides, issue resolution, FAQs"),
    
    // Business & Research
    BUSINESS_DOCS("Business Documents", "Reports, presentations, analysis"),
    RESEARCH_PAPERS("Research Papers", "Academic papers, industry research"),
    MARKET_INTELLIGENCE("Market Intelligence", "Competitive analysis, trends"),
    IMMIGRATION_DOCS("Immigration Documents", "Visa applications, permits, residency documents"),
    
    // Training & Education
    TRAINING_MATERIALS("Training Materials", "Onboarding docs, courses, tutorials"),
    BEST_PRACTICES("Best Practices", "Guidelines, standards, methodologies"),
    
    // Product & Product Management
    PRODUCT_DOCS("Product Documentation", "Specs, requirements, roadmaps"),
    USER_GUIDES("User Guides", "Manuals, how-tos, tutorials"),
    
    // Operations
    POLICIES("Policies", "Company policies, procedures, governance"),
    SOP("Standard Operating Procedures", "Processes, workflows, checklists"),
    
    // Others
    ARCHIVES("Archives", "Historical documents, old files"),
    TEMPLATES("Templates", "Document templates, forms"),
    REFERENCE("Reference Material", "Glossaries, indexes, directories"),
    CUSTOM("Custom", "Custom category");
    
    private final String displayName;
    private final String description;
    
    KnowledgeCategory(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
}

