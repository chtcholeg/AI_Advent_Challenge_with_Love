# Docker MCP Server - Usage Examples

This document provides practical examples of using the Docker MCP Server through AI conversations.

## Quick Start Scenarios

### 1. Start a Development Database

**User**: "I need a PostgreSQL database for development"

**AI Response**: "I'll start a PostgreSQL container for you."

**Tool Call**:
```json
{
  "name": "docker_run",
  "arguments": {
    "image": "postgres:15",
    "name": "dev-postgres",
    "ports": ["5432:5432"],
    "env": {
      "POSTGRES_USER": "dev",
      "POSTGRES_PASSWORD": "dev123",
      "POSTGRES_DB": "myapp"
    },
    "detach": true
  }
}
```

**Result**: PostgreSQL running on `localhost:5432`, credentials: `dev/dev123`

---

### 2. Run a Web Server

**User**: "Start nginx to serve files from /Users/me/website on port 8080"

**Tool Call**:
```json
{
  "name": "docker_run",
  "arguments": {
    "image": "nginx:alpine",
    "name": "my-website",
    "ports": ["8080:80"],
    "volumes": ["/Users/me/website:/usr/share/nginx/html:ro"],
    "detach": true
  }
}
```

**Result**: Nginx serving at `http://localhost:8080`

---

### 3. Check Running Containers

**User**: "What containers are running?"

**Tool Call**:
```json
{
  "name": "docker_ps",
  "arguments": {
    "all": false
  }
}
```

**Result**: Table showing container ID, name, image, status, and ports

---

### 4. View Container Logs

**User**: "Show me the last 50 logs from dev-postgres"

**Tool Call**:
```json
{
  "name": "docker_logs",
  "arguments": {
    "container": "dev-postgres",
    "tail": 50
  }
}
```

**Result**: Last 50 log lines from the PostgreSQL container

---

### 5. Execute Commands in Container

**User**: "Connect to PostgreSQL and list databases"

**Tool Call**:
```json
{
  "name": "docker_exec",
  "arguments": {
    "container": "dev-postgres",
    "command": "psql -U dev -c '\\l'"
  }
}
```

**Result**: List of PostgreSQL databases

---

### 6. Stop a Container

**User**: "Stop the nginx container"

**Tool Call**:
```json
{
  "name": "docker_stop",
  "arguments": {
    "container": "my-website",
    "timeout": 10
  }
}
```

**Result**: Container stopped gracefully

---

## Complex Scenarios

### Development Environment Setup

**User**: "Set up a full dev environment with PostgreSQL, Redis, and a web server"

**Step 1**: Start PostgreSQL
```json
{
  "name": "docker_run",
  "arguments": {
    "image": "postgres:15",
    "name": "dev-postgres",
    "ports": ["5432:5432"],
    "env": {
      "POSTGRES_PASSWORD": "dev",
      "POSTGRES_DB": "app"
    }
  }
}
```

**Step 2**: Start Redis
```json
{
  "name": "docker_run",
  "arguments": {
    "image": "redis:7-alpine",
    "name": "dev-redis",
    "ports": ["6379:6379"]
  }
}
```

**Step 3**: Start Nginx
```json
{
  "name": "docker_run",
  "arguments": {
    "image": "nginx:alpine",
    "name": "dev-nginx",
    "ports": ["8080:80"]
  }
}
```

**Step 4**: Verify all running
```json
{
  "name": "docker_ps",
  "arguments": {}
}
```

---

### Using Docker Compose

**User**: "Start all services from my docker-compose.yml"

**Tool Call**:
```json
{
  "name": "docker_compose_up",
  "arguments": {
    "file": "docker-compose.yml",
    "detach": true,
    "build": false
  }
}
```

**Result**: All services from compose file started

---

**User**: "Check status of compose services"

**Tool Call**:
```json
{
  "name": "docker_compose_ps",
  "arguments": {
    "file": "docker-compose.yml"
  }
}
```

---

**User**: "Stop everything and remove volumes"

**Tool Call**:
```json
{
  "name": "docker_compose_down",
  "arguments": {
    "file": "docker-compose.yml",
    "volumes": true
  }
}
```

---

### Image Management

**User**: "Pull the latest Python image"

**Tool Call**:
```json
{
  "name": "docker_pull",
  "arguments": {
    "image": "python:3.11-slim"
  }
}
```

---

**User**: "Show me all Docker images"

**Tool Call**:
```json
{
  "name": "docker_images",
  "arguments": {}
}
```

---

**User**: "Build an image from my Dockerfile and tag it as myapp:1.0"

**Tool Call**:
```json
{
  "name": "docker_build",
  "arguments": {
    "path": ".",
    "tag": "myapp:1.0",
    "dockerfile": "Dockerfile"
  }
}
```

---

## Debugging Scenarios

### Container Not Starting

**User**: "Why isn't my container starting?"

**AI Process**:

1. Check if container exists:
```json
{"name": "docker_ps", "arguments": {"all": true}}
```

2. View logs:
```json
{"name": "docker_logs", "arguments": {"container": "my-app", "tail": 100}}
```

3. Inspect running processes:
```json
{"name": "docker_exec", "arguments": {"container": "my-app", "command": "ps aux"}}
```

---

### Port Conflicts

**User**: "I'm getting a port conflict error"

**AI Process**:

1. List all containers with ports:
```json
{"name": "docker_ps", "arguments": {"all": false}}
```

2. Stop conflicting container:
```json
{"name": "docker_stop", "arguments": {"container": "conflicting-container"}}
```

3. Start your container:
```json
{"name": "docker_run", "arguments": {...}}
```

---

### Container Health Check

**User**: "Is my PostgreSQL container healthy?"

**AI Process**:

1. Check status:
```json
{"name": "docker_ps", "arguments": {}}
```

2. View logs:
```json
{"name": "docker_logs", "arguments": {"container": "postgres", "tail": 20}}
```

3. Test connection:
```json
{
  "name": "docker_exec",
  "arguments": {
    "container": "postgres",
    "command": "pg_isready -U postgres"
  }
}
```

---

## Testing Scenarios

### Integration Testing Environment

**User**: "Set up an environment for running integration tests"

```json
{
  "name": "docker_compose_up",
  "arguments": {
    "file": "docker-compose.test.yml",
    "detach": true,
    "build": true
  }
}
```

After tests:
```json
{
  "name": "docker_compose_down",
  "arguments": {
    "file": "docker-compose.test.yml",
    "volumes": true
  }
}
```

---

### Database Migration Testing

**User**: "I need to test database migrations"

**Steps**:

1. Start clean PostgreSQL:
```json
{
  "name": "docker_run",
  "arguments": {
    "image": "postgres:15",
    "name": "migration-test",
    "ports": ["5433:5432"],
    "env": {"POSTGRES_PASSWORD": "test"},
    "remove": true
  }
}
```

2. Run migrations:
```json
{
  "name": "docker_exec",
  "arguments": {
    "container": "migration-test",
    "command": "psql -U postgres -c 'CREATE DATABASE test;'"
  }
}
```

3. Clean up automatically when stopped (--rm flag)

---

## Production-Like Scenarios

### Multi-Service Application

**User**: "Start my microservices stack"

Using Docker Compose:
```json
{
  "name": "docker_compose_up",
  "arguments": {
    "file": "docker-compose.yml",
    "detach": true
  }
}
```

Check health:
```json
{"name": "docker_compose_ps", "arguments": {"file": "docker-compose.yml"}}
```

View specific service logs:
```json
{"name": "docker_logs", "arguments": {"container": "api-service", "tail": 100}}
```

---

### Rolling Updates

**User**: "Update my API service without downtime"

1. Start new version:
```json
{
  "name": "docker_run",
  "arguments": {
    "image": "myapi:v2",
    "name": "api-v2",
    "ports": ["8081:8000"]
  }
}
```

2. Verify it's healthy:
```json
{"name": "docker_logs", "arguments": {"container": "api-v2", "tail": 20}}
```

3. Stop old version:
```json
{"name": "docker_stop", "arguments": {"container": "api-v1"}}
```

---

## Cleanup Scenarios

### Stop All Containers

**User**: "Stop all running containers"

**AI Process**:

1. List containers:
```json
{"name": "docker_ps", "arguments": {}}
```

2. Stop each container:
```json
{"name": "docker_stop", "arguments": {"container": "container-1"}}
{"name": "docker_stop", "arguments": {"container": "container-2"}}
...
```

---

### Clean Development Environment

**User**: "Clean up my development environment"

```json
{
  "name": "docker_compose_down",
  "arguments": {
    "file": "docker-compose.dev.yml",
    "volumes": true
  }
}
```

---

## Tips for Effective Usage

### 1. Naming Containers
Always name your containers for easy management:
```json
"name": "my-postgres"  // Good
// vs no name - auto-generated random name
```

### 2. Using Volumes for Persistence
```json
"volumes": ["/local/data:/container/data"]
```

### 3. Environment Variables
```json
"env": {
  "DB_HOST": "postgres",
  "DB_PORT": "5432",
  "DEBUG": "true"
}
```

### 4. Port Mapping
```json
"ports": ["host_port:container_port"]
// Example: ["8080:80"] - access container's port 80 via localhost:8080
```

### 5. Resource Limits (Note: Not yet implemented)
For production, consider:
- Memory limits
- CPU limits
- Network isolation

---

## Common Patterns

### Pattern 1: Database Setup
```
1. docker_run (start database)
2. docker_logs (verify startup)
3. docker_exec (run initialization scripts)
```

### Pattern 2: Development Workflow
```
1. docker_compose_up (start all services)
2. docker_logs (monitor logs)
3. docker_exec (run commands)
4. docker_compose_down (cleanup)
```

### Pattern 3: Testing
```
1. docker_run (start test environment)
2. docker_exec (run tests)
3. docker_logs (check results)
4. docker_stop (cleanup)
```

### Pattern 4: Debugging
```
1. docker_ps (check status)
2. docker_logs (view errors)
3. docker_exec (inspect state)
4. docker_restart (try fix)
```

---

## Limitations to Know

1. **No Interactive TTY**: Commands run non-interactively
2. **No Log Following**: Can't stream logs in real-time
3. **Timeout Limits**: Long-running operations may timeout
4. **Local Only**: Only manages local Docker daemon

---

## Security Best Practices

1. **Don't Hardcode Secrets**: Use environment variables
2. **Use Read-Only Volumes**: Add `:ro` flag when possible
3. **Limit Port Exposure**: Only expose necessary ports
4. **Use Official Images**: Prefer verified Docker Hub images
5. **Clean Up Regularly**: Remove unused containers and volumes
