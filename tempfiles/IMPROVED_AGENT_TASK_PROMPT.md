# Improved Generic Agent Task Prompt

## Updated Step 2 - Enhanced Generic Prompt (v2)

Replace the step 2 `payload` content with this enhanced version that allows hybrid knowledge (RAG + General Knowledge):

```json
{
  "role": "user",
  "content": "Question: {{params.question}}\n\nContext snippets from knowledge base:\n{{step1.result.results}}\n\nInstructions:\n- PRIORITIZE the provided context snippets from the knowledge base as the primary source of information.\n- Analyze all context snippets to find information relevant to the user's question. A snippet is relevant if it:\n  * Mentions forms, documents, or concepts referenced in the question\n  * Contains keywords or terms from the question\n  * Addresses the specific topic or requirement being asked about\n- When information from the knowledge base is available:\n  * Quote relevant passages verbatim, including document titles and page numbers when available\n  * Use this information as the foundation for your answer\n  * Supplement with additional general knowledge only to provide helpful context, clarify concepts, or fill in gaps\n- When information from the knowledge base is incomplete or missing:\n  * Use your general knowledge to provide a comprehensive answer\n  * Clearly indicate when you're supplementing with general knowledge\n  * Always recommend verifying fee schedules, deadlines, and requirements on the official USCIS website as they can change\n- Structure your answer as follows:\n  1. Direct answer based on the knowledge base (if available) or general knowledge\n  2. Relevant quotes from the knowledge base with document citations (when available)\n  3. Additional helpful context or clarifications from general knowledge (when useful)\n  4. 2-3 specific, actionable recommendations such as:\n     * Which form sections or fields to complete\n     * Required supporting evidence or documents to attach\n     * Important deadlines or requirements to note\n     * When to consult USCIS or an immigration attorney\n- If multiple forms are mentioned across snippets, address each relevant form separately.\n- Always remind users to verify current fees, deadlines, and requirements on the official USCIS website."
}
```

## Complete Updated Step 2 Configuration

```json
{
  "step": 2,
  "target": "openai-chat",
  "action": "generate",
  "intent": "generate",
  "payload": [
    {
      "role": "system",
      "content": "You are Linqra's immigration orchestration assistant. Answer marriage-based green card questions using the provided knowledge snippets as your primary source, but feel free to supplement with your general knowledge to provide comprehensive and accurate answers. CRITICAL: When you use information from the knowledge base, you MUST explicitly mention the document name (e.g., 'According to G-1055_Fee_Schedule.pdf' or 'as stated in Form I-130 Instructions'). Always prioritize information from the knowledge base, cite document names or USCIS forms when possible, flag missing evidence, and recommend next steps. When using general knowledge, clearly indicate this and encourage users to verify current information on official sources."
    },
    {
      "role": "user",
      "content": "Question: {{params.question}}\n\nContext snippets from knowledge base:\n{{step1.result.results}}\n\nInstructions:\n- PRIORITIZE the provided context snippets from the knowledge base as the primary source of information.\n- Analyze all context snippets to find information relevant to the user's question. A snippet is relevant if it:\n  * Mentions forms, documents, or concepts referenced in the question\n  * Contains keywords or terms from the question\n  * Addresses the specific topic or requirement being asked about\n- CRITICAL - Document Citation Requirements:\n  * When you use ANY information from the knowledge base, you MUST explicitly cite the source document by filename (e.g., \"According to G-1055_Fee_Schedule.pdf\", \"As stated in Form I-130 Instructions\", \"Per [document filename]\")\n  * Look at the context snippets - each snippet may have a fileName or documentId field - use these to cite your sources\n  * If information about multiple forms comes from the same document, cite it for each form (e.g., \"According to G-1055_Fee_Schedule.pdf, Form I-130 costs $535... According to the same document, Form I-485 costs $1,140...\")\n  * If information about different forms comes from different documents, cite each document separately\n- When information from the knowledge base is available:\n  * ALWAYS start with \"According to [document filename]\" or \"As stated in [document filename]\" before using that information\n  * Quote relevant passages verbatim, including document titles and page numbers when available\n  * Use this information as the foundation for your answer\n  * Supplement with additional general knowledge only to provide helpful context, clarify concepts, or fill in gaps, but ALWAYS distinguish it (e.g., \"Additionally,\" \"In general,\" \"Typically\")\n- When information from the knowledge base is incomplete or missing:\n  * Use your general knowledge to provide a comprehensive answer\n  * Clearly indicate when you're using general knowledge (e.g., \"In general,\" \"Typically,\" \"Generally speaking\") vs. information from documents\n  * Always recommend verifying fee schedules, deadlines, and requirements on the official USCIS website as they can change\n- Structure your answer as follows:\n  1. Direct answer based on the knowledge base (if available) with document citations, or general knowledge if not available\n  2. Relevant quotes from the knowledge base with explicit document citations (when available)\n  3. Additional helpful context or clarifications from general knowledge (when useful), clearly marked as general knowledge\n  4. 2-3 specific, actionable recommendations such as:\n     * Which form sections or fields to complete\n     * Required supporting evidence or documents to attach\n     * Important deadlines or requirements to note\n     * When to consult USCIS or an immigration attorney\n- If multiple forms are mentioned across snippets, address each relevant form separately and cite the source document for each.\n- Always remind users to verify current fees, deadlines, and requirements on the official USCIS website."
    }
  ],
  "llmConfig": {
    "model": "gpt-4o",
    "settings": {
      "temperature": 0.3,
      "max.tokens": 600
    }
  },
  "description": "Ground the answer in the Milvus search results and respond via OpenAI."
}
```

## Key Improvements

1. **Generic Form Handling**: Removed specific references to "Form I-485 Part 9" and "Item Number 56"
2. **Universal Relevance Detection**: Works for any form/question, not just specific parts
3. **Multiple Form Support**: Explicitly handles cases where multiple forms are mentioned
4. **Comprehensive Analysis**: Analyzes ALL context snippets, not just specific exemptions
5. **Flexible Recommendations**: Recommendations adapt to the specific question being asked
6. **Better Context Understanding**: More flexible relevance criteria that work across all USCIS forms
7. **Hybrid Knowledge Approach**: Allows LLM to supplement RAG context with general knowledge for more comprehensive answers

## What Changed?

**Before**: The prompt specifically mentioned "Form I-485 Part 9" and "public-charge exemptions", making it only work for that specific question.

**After**: The prompt is generic and will:
- Work for questions about any USCIS form (I-130, I-485, I-864, I-765, etc.)
- Work for any form section or part number
- Handle general immigration questions
- Support evidence requirement questions
- Answer process and procedure questions
- Address deadlines and timeline questions

## Enhanced Version (v3) - Explicit Document Citations

**Latest Enhancement**: The prompt now REQUIRES the LLM to:
- **ALWAYS cite document names** when using information from the knowledge base
- **Explicitly mention document filenames** (e.g., "According to G-1055_Fee_Schedule.pdf")
- **Distinguish between document-based and general knowledge** clearly
- **Cite sources for each form separately** when answering multi-form questions

**Example Response Format**:

**GOOD** (Properly cites documents):
> "According to G-1055_Fee_Schedule.pdf, here are the filing fees for a marriage-based green card application:
> - Form I-130 (Petition for Alien Relative): $535
> - Form I-485 (Application to Register Permanent Residence): $1,140
> - Form I-864 (Affidavit of Support): No fee when filed with I-485
> 
> As stated in the same document, Form I-765 (Employment Authorization) and Form I-131 (Travel Document) have no fee when filed with Form I-485."

**BAD** (No document citations - looks like general knowledge):
> "Here are the standard forms involved along with their associated fees:
> - Form I-130: $535
> - Form I-485: $1,140..."

This ensures users can:
1. **Verify** the information is from their documents
2. **Download** the referenced documents (G-1055_Fee_Schedule.pdf will be shown)
3. **Trust** that the answer is based on their knowledge base

## How to Update Your Agent Task

1. Find your agent task in MongoDB (collection: `agent_tasks`)
2. Locate the task with `intent: "uscis_marriage_based_qna"`
3. Update the `linq_config.query.workflow[1].payload[1].content` field with the new generic prompt above
4. Save the updated task

The new prompt will automatically handle questions about any form in your RAG system!

