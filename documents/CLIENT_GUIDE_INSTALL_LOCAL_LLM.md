# Client Guide: Installing a Local Private LLM

This document provides step-by-step instructions for setting up a private, local Large Language Model (LLM) server using **Ollama**. This ensures all AI data processing remains strictly within your private network infrastructure.

## 1. Hardware Requirements
For acceptable performance in an enterprise environment, we recommend provisioning a dedicated Linux server (or Virtual Machine) with the following specifications:

*   **OS:** Ubuntu 22.04 LTS (Recommended) or other Linux distribution.
*   **GPU:** NVIDIA GPU is **essential** for inference speed.
    *   *Minimum:* NVIDIA T4 (16GB VRAM)
    *   *Recommended:* NVIDIA A10G (24GB VRAM) or A100.
*   **RAM:** 32GB System RAM.
*   **Disk:** 100GB+ SSD (Metric for model storage).

## 2. Installation Steps

### Step 1: Install Ollama
Run the following command on your Linux server to install the Ollama service:

```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### Step 2: Configure Network Access
By default, Ollama is locked to `localhost` (127.0.0.1) for security. To allow other applications (like Linqra) on your internal network to access it, you must bind it to `0.0.0.0`.

1.  Edit the systemd service configuration:
    ```bash
    sudo systemctl edit ollama.service
    ```

2.  Add the following lines in the editor window:
    ```ini
    [Service]
    Environment="OLLAMA_HOST=0.0.0.0"
    Environment="OLLAMA_ORIGINS=*"
    ```

3.  Save and exit (Ctrl+O, Enter, Ctrl+X).

4.  Apply changes and restart the service:
    ```bash
    sudo systemctl daemon-reload
    sudo systemctl restart ollama
    ```

### Step 3: verify Installation
Confirm the service is running and listening on the correct port:
```bash
netstat -tulpn | grep 11434
# You should see tcp6 ... :::11434 LISTEN
```

## 3. Managing Models

### Downloading Models
You must download ("pull") the specific models you wish to use. We recommend the following for enterprise tasks:

```bash
# 1. Meta Llama 3 (8B) - Excellent general purpose model, fast.
ollama pull llama3

# 2. Mistral (7B) - High reasoning capabilities.
ollama pull mistral

# 3. Nomic Embed Text - Required for RAG (Retrieval Augmented Generation) features.
ollama pull nomic-embed-text
```

### Listing Installed Models
To see what is currently available on your server:
```bash
ollama list
```

## 4. Connection Details
Your private LLM is now ready. Provide the following details to your application integration team:

*   **Base URL:** `http://<YOUR-SERVER-IP>:11434/v1`
*   **Chat Endpoint:** `http://<YOUR-SERVER-IP>:11434/v1/chat/completions`
*   **Embeddings Endpoint:** `http://<YOUR-SERVER-IP>:11434/v1/embeddings`
*   **API Key:** `ollama` (Required by some SDKs, but ignored by the server).

## 5. Security Note
This installation exposes the LLM API to anyone on your internal network who can reach port `11434`. Ensure your **Network Security Groups (firewall)** restrict access to port `11434` strictly to:
1.  The Application Server hosting Linqra.
2.  Admin VPNs/Bastion hosts for maintenance.
