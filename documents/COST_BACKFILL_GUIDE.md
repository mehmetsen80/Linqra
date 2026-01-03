# LLM Cost Backfill Guide

## Overview

The **Cost Backfill** feature provides TWO levels of backfill for retroactively calculating costs for existing workflow executions:

### **Level 1: Advanced Token Usage Backfill** (For Cohere & Similar)
Extracts token usage from response data when it wasn't originally captured in metadata. This is needed for:
- ✅ Cohere executions created before token tracking was implemented
- ✅ Executions where `response.result.steps[X].result.meta.billed_units` exists but `workflowMetadata[X].tokenUsage` is missing
- ✅ Retroactively populating token data from response payloads

### **Level 2: Regular Cost Backfill** (For All Providers)
Updates costs for executions that already have token usage in metadata. This is useful when:
- ✅ Token usage was tracked but cost calculation was missing or failed
- ✅ Pricing information was updated and you want to recalculate historical costs
- ✅ Model information is missing but token usage exists

---

## How It Works

### **🔍 Advanced Token Usage Backfill** (Run FIRST for Cohere)

#### 1. **Detection Phase**
Scans all workflow executions and identifies those where:
- LLM step is `cohere` or `cohere-embed`
- `workflowMetadata[step].tokenUsage` is **missing** (null)
- BUT `result.steps[step].result.meta.billed_units` **exists**

#### 2. **Extraction Phase**
For each Cohere step:
- Reads token data from `response.result.steps[X].result.meta.billed_units`:
  - `input_tokens` → `promptTokens`
  - `output_tokens` → `completionTokens`
- Extracts **model** from `request.query.workflow[step].llmConfig.model`
- Falls back to `command-r-08-2024` if model not specified

#### 3. **Population Phase**
Creates and populates `tokenUsage` object:
```javascript
{
  "promptTokens": 128,
  "completionTokens": 29,
  "totalTokens": 157,
  "costUsd": 0.000193  // Calculated
}
```

#### 4. **Update Phase**
- Sets `workflowMetadata[step].tokenUsage` with extracted data
- Sets `workflowMetadata[step].model` 
- Saves execution record to MongoDB

---

### **💰 Regular Cost Backfill** (Run SECOND)

#### 1. **Detection Phase**
Scans executions that:
- Have LLM steps with `tokenUsage` already populated
- Are missing `costUsd` OR missing `model` field

#### 2. **Extraction Phase**
- Extracts **model** from `request.query.workflow[step].llmConfig.model`
- Uses existing **token usage** from `workflowMetadata[step].tokenUsage`
- Falls back to default models:
  - OpenAI: `gpt-4o-mini`
  - Gemini: `gemini-2.0-flash`
  - Cohere: `command-r-08-2024`

#### 3. **Calculation Phase**
- Looks up model pricing (input/output price per 1M tokens)
- Calculates: `cost = (promptTokens × inputPrice + completionTokens × outputPrice) / 1,000,000`

#### 4. **Update Phase**
- Updates `tokenUsage.costUsd` if missing
- Updates `stepMetadata.model` if missing
- Saves execution record to MongoDB

---

## API Endpoints

### **1. Advanced Token Usage Backfill** ⭐ NEW

**POST** `/api/dashboard/backfill-token-usage`

Extracts token usage from Cohere response data when it's missing from metadata.

#### Query Parameters:

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `teamId` | String | No | `null` | Limit backfill to specific team (all teams if not provided) |
| `dryRun` | Boolean | No | `true` | If `true`, only logs what would be extracted without making changes |

#### Response:

```json
{
  "success": true,
  "message": "Token usage backfill completed successfully",
  "updatedExecutions": 88,
  "dryRun": false,
  "teamId": "67d0aeb17172416c411d419e"
}
```

---

### **2. Regular Cost Backfill**

**POST** `/api/dashboard/backfill-costs`

Updates costs for executions that already have tokenUsage in metadata.

#### Query Parameters:

| Parameter | Type | Required | Default | Description |
|-----------|------|----------|---------|-------------|
| `teamId` | String | No | `null` | Limit backfill to specific team (all teams if not provided) |
| `dryRun` | Boolean | No | `true` | If `true`, only logs what would be updated without making changes |

#### Response:

```json
{
  "success": true,
  "message": "Cost backfill completed successfully",
  "updatedExecutions": 2,
  "dryRun": false,
  "teamId": "67d0aeb17172416c411d419e"
}
```

---

## Usage Examples

### **⚠️ IMPORTANT: Two-Step Process for Cohere**

If you have Cohere executions:
1. **FIRST**: Run Advanced Token Usage Backfill (extracts tokenUsage from response)
2. **SECOND**: Run Regular Cost Backfill (calculates costs)

---

### **Step 1: Advanced Token Usage Backfill** (For Cohere)

#### Dry Run:
```bash
curl -k -X POST 'https://localhost:7777/api/dashboard/backfill-token-usage?teamId=YOUR_TEAM_ID&dryRun=true'
```

#### Actual Update:
```bash
curl -k -X POST 'https://localhost:7777/api/dashboard/backfill-token-usage?teamId=YOUR_TEAM_ID&dryRun=false'
```

**What this does:**
- ✅ Finds Cohere steps without `tokenUsage` in metadata
- ✅ Extracts token counts from `response.result.steps[X].result.meta.billed_units`
- ✅ Creates `tokenUsage` object with `promptTokens`, `completionTokens`, `totalTokens`
- ✅ Calculates and stores `costUsd`
- ✅ Sets the `model` field

---

### **Step 2: Regular Cost Backfill** (For All Providers)

#### Dry Run - All Teams:
```bash
curl -k -X POST 'https://localhost:7777/api/dashboard/backfill-costs?dryRun=true'
```

#### Dry Run - Specific Team:
```bash
curl -k -X POST 'https://localhost:7777/api/dashboard/backfill-costs?teamId=YOUR_TEAM_ID&dryRun=true'
```

#### Actual Update:
```bash
curl -k -X POST 'https://localhost:7777/api/dashboard/backfill-costs?teamId=YOUR_TEAM_ID&dryRun=false'
```

**What this does:**
- ✅ Finds executions with `tokenUsage` but missing `costUsd` or `model`
- ✅ Calculates costs using current pricing
- ✅ Updates metadata fields

---

## Log Output Example

When running the backfill, you'll see logs like this in the api-gateway:

```
🔄 Starting cost backfill for executions (dryRun: true, teamId: 67d0aeb17172416c411d419e)

📊 [DRY RUN] execution 68f6f8fc238bf07c5f590bdf - step 3 (cohere): model=command-r-08-2024, tokens=159p/16c, cost=$0.000184

📊 [DRY RUN] execution 68f6f8fc238bf07c5f590bdf - step 4 (cohere): model=command-r-08-2024, tokens=43p/1c, cost=$0.000016

✅ [DRY RUN] Would update 1 executions with backfilled costs
```

When running with `dryRun=false`, you'll see:
```
📊 Updating execution 68f6f8fc238bf07c5f590bdf - step 3 (cohere): model=command-r-08-2024, tokens=159p/16c, cost=$0.000184

✅ Successfully backfilled costs for 1 executions
```

---

## Workflow Example

### Before Backfill:

```json
{
  "_id": "68f6f8fc238bf07c5f590bdf",
  "response": {
    "metadata": {
      "workflowMetadata": [
        {
          "step": 3,
          "target": "cohere-chat",
          "tokenUsage": {
            "promptTokens": 159,
            "completionTokens": 16,
            "totalTokens": 175,
            "costUsd": null  // ← Missing cost!
          }
        }
      ]
    }
  }
}
```

### After Backfill:

```json
{
  "_id": "68f6f8fc238bf07c5f590bdf",
  "response": {
    "metadata": {
      "workflowMetadata": [
        {
          "step": 3,
          "target": "cohere-chat",
          "model": "command-r-08-2024",  // ← Model added
          "tokenUsage": {
            "promptTokens": 159,
            "completionTokens": 16,
            "totalTokens": 175,
            "costUsd": 0.000184  // ← Cost calculated!
          }
        }
      ]
    }
  }
}
```

---

## Supported LLM Providers

The backfill process supports all LLM providers:

| Provider | Targets | Pricing Source |
|----------|---------|----------------|
| **OpenAI** | `openai-chat`, `openai-embed` | `LlmCostServiceImpl.MODEL_PRICING` |
| **Gemini** | `gemini-chat`, `gemini-embed` | `LlmCostServiceImpl.MODEL_PRICING` |
| **Cohere** | `cohere-chat`, `cohere-embed` | `LlmCostServiceImpl.MODEL_PRICING` |

---

## Cost Calculation Details

### OpenAI Example:
- **Model**: `gpt-4o-mini`
- **Pricing**: $0.15/1M input, $0.60/1M output
- **Token Usage**: 1000 prompt + 500 completion
- **Cost**: `(1000 × 0.15 + 500 × 0.60) / 1,000,000 = $0.00045`

### Cohere Example:
- **Model**: `command-r-08-2024`
- **Pricing**: $0.15/1M input, $0.60/1M output
- **Token Usage**: 159 prompt + 16 completion
- **Cost**: `(159 × 0.15 + 16 × 0.60) / 1,000,000 = $0.000184`

### Cohere Embedding Example:
- **Model**: `embed-v4.0`
- **Pricing**: $0.12/1M input, $0.00/1M output
- **Token Usage**: 50 tokens
- **Cost**: `(50 × 0.12 + 0 × 0.00) / 1,000,000 = $0.000006`

---

## Best Practices

### 1. **Always Run Dry Run First**
```bash
# Check what would be updated
curl -X POST 'http://localhost:8080/api/dashboard/backfill-costs?dryRun=true'
```

### 2. **Review Logs**
- Check the api-gateway logs to see which executions will be updated
- Verify the models and costs look correct

### 3. **Test with a Single Team First**
```bash
# Update only one team first
curl -X POST 'http://localhost:8080/api/dashboard/backfill-costs?teamId=YOUR_TEAM_ID&dryRun=false'
```

### 4. **Verify Results**
- Check a few execution records in MongoDB
- Verify costs appear correctly in the LLM Usage dashboard

### 5. **Run for All Teams**
```bash
# Once verified, update all teams
curl -X POST 'http://localhost:8080/api/dashboard/backfill-costs?dryRun=false'
```

---

## Troubleshooting

### Issue: "No executions found to update"
- **Cause**: All executions already have cost data
- **Solution**: Check if costs were already calculated

### Issue: "Missing model information"
- **Cause**: Workflow didn't specify `llmConfig.model`
- **Solution**: Default model will be used (check logs)

### Issue: "Unknown model pricing"
- **Cause**: Model not in `MODEL_PRICING` map
- **Solution**: Add model pricing to `LlmCostServiceImpl.MODEL_PRICING`

---

## Using the Helper Script

A helper script is provided: `backfill_costs_example.sh`

```bash
# Make it executable
chmod +x backfill_costs_example.sh

# Run it
./backfill_costs_example.sh
```

The script will:
1. Run a dry run for all teams
2. Run a dry run for a specific team
3. Show (commented out) how to run actual updates

---

## MongoDB Query to Check Results

### Accurate Count of Executions Needing Backfill:
This query matches exactly what the API counts - only LLM steps with token usage but missing cost:

```javascript
db.linq_workflow_executions.find({
  teamId: "YOUR_TEAM_ID",
  "response.metadata.workflowMetadata": {
    $elemMatch: {
      target: { $in: ["openai-chat", "gemini-chat", "cohere-chat", "claude-chat", "openai-embed", "gemini-embed", "cohere-embed"] },
      "tokenUsage.promptTokens": { $exists: true },
      "tokenUsage.completionTokens": { $exists: true },
      $or: [
        { "tokenUsage.costUsd": null },
        { "tokenUsage.costUsd": 0.0 }
      ]
    }
  }
}).count()
```

**Note:** This is more accurate than just checking `tokenUsage.costUsd: null` because it filters out non-LLM steps that never have token usage.

### After Backfill - Verify Costs Were Added:
```javascript
db.linq_workflow_executions.find({
  teamId: "YOUR_TEAM_ID",
  "response.metadata.workflowMetadata": {
    $elemMatch: {
      target: { $in: ["openai-chat", "gemini-chat", "cohere-chat", "claude-chat", "openai-embed", "gemini-embed", "cohere-embed"] },
      "tokenUsage.costUsd": { $exists: true, $ne: null, $gt: 0 }
    }
  }
}).count()
```

### View a Sample Updated Execution:
```javascript
db.linq_workflow_executions.findOne({
  teamId: "YOUR_TEAM_ID",
  "response.metadata.workflowMetadata.tokenUsage.costUsd": { $gt: 0 }
}, {
  "response.metadata.workflowMetadata": 1,
  "_id": 1
})
```

---

## Summary

The Cost Backfill feature provides a safe and efficient way to retroactively calculate costs for historical workflow executions. By supporting dry runs, team-specific updates, and detailed logging, it ensures you can confidently update your cost data without risk.

**Key Benefits:**
- ✅ Recalculate costs for any LLM provider (OpenAI, Gemini, Cohere)
- ✅ Safe dry-run mode to preview changes
- ✅ Team-specific or global updates
- ✅ Detailed logging for audit trail
- ✅ Automatic fallback to default models
- ✅ No data loss - only adds missing cost information

---

**Questions?** Check the logs in `api-gateway` for detailed execution information.

