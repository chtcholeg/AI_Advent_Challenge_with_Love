# Ollama Setup Guide

This guide explains how to set up Ollama for use with the Document Indexer module.

## What is Ollama?

Ollama is a tool for running large language models locally. The Document Indexer uses Ollama to generate embeddings for semantic search using the `nomic-embed-text` model.

## Installation

### macOS
```bash
brew install ollama
```

### Linux
```bash
curl -fsSL https://ollama.com/install.sh | sh
```

### Windows
Download from [ollama.com/download](https://ollama.com/download)

## Setup

### 1. Start Ollama Server

```bash
ollama serve
```

The server will start on `http://localhost:11434` by default.

### 2. Pull the Embedding Model

```bash
ollama pull nomic-embed-text
```

This downloads the `nomic-embed-text` model (~270MB), which is optimized for generating text embeddings.

### 3. Verify Installation

```bash
# Check if server is running
curl http://localhost:11434/api/tags

# Test embedding generation
curl http://localhost:11434/api/embed -d '{
  "model": "nomic-embed-text",
  "input": ["Hello, world!"]
}'
```

## Running the Indexer

Once Ollama is set up:

```bash
./gradlew :indexer:run
```

The application will:
1. Check Ollama availability (green indicator = connected)
2. Allow you to select files/directories for indexing
3. Generate embeddings using Ollama
4. Store indexed content in SQLite database
5. Enable semantic search across indexed documents

## Configuration

### Default Settings

| Setting | Value |
|---------|-------|
| Base URL | `http://localhost:11434` |
| Model | `nomic-embed-text` |
| Batch Size | 10 texts per request |
| Database | `~/.document-indexer/indexer.db` |

### Chunk Settings

| Setting | Default |
|---------|---------|
| Strategy | By characters |
| Chunk Size | 500 characters |
| Overlap | 50 characters |

## Supported File Types

The indexer supports the following file extensions by default:
- `.md` (Markdown)
- `.txt` (Plain text)

## Troubleshooting

### Ollama Status Shows "Offline"

1. Ensure Ollama is running: `ollama serve`
2. Check if the port is accessible: `curl http://localhost:11434/api/tags`
3. Verify no firewall blocking port 11434

### Embedding Generation Fails

1. Ensure the model is pulled: `ollama list`
2. If model missing: `ollama pull nomic-embed-text`
3. Check Ollama logs for errors

### Slow Indexing

- Embedding generation is CPU/GPU intensive
- Consider indexing fewer files at once
- Ensure Ollama has sufficient resources

### Database Issues

The database is stored at `~/.document-indexer/indexer.db`. To reset:
```bash
rm ~/.document-indexer/indexer.db
```

## API Reference

### Ollama Embed Endpoint

The indexer uses the `/api/embed` endpoint:

**Request:**
```json
{
  "model": "nomic-embed-text",
  "input": ["text1", "text2", ...]
}
```

**Response:**
```json
{
  "model": "nomic-embed-text",
  "embeddings": [[0.1, 0.2, ...], [0.3, 0.4, ...]]
}
```

### Embedding Dimensions

The `nomic-embed-text` model produces 768-dimensional vectors.

## Alternative Models

You can use other embedding models available in Ollama:

```bash
ollama pull mxbai-embed-large  # 1024 dimensions
ollama pull all-minilm         # 384 dimensions
```

To use a different model, modify `OllamaApi.DEFAULT_MODEL` in the code.
