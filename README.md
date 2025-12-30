# DFC Bridge

A Java Spring Boot microservice that wraps Documentum Foundation Classes (DFC) and exposes a REST API for external applications to communicate with Documentum repositories.

## Overview

DFC Bridge provides a lightweight HTTP bridge to DFC, enabling non-Java applications (like the Documentum VS Code extension) to interact with Documentum repositories through a simple REST API.

### Architecture

```
Client Application (e.g., VS Code Extension)
           │
           │ HTTP/REST
           ▼
    ┌──────────────┐
    │  DFC Bridge  │
    │ (Spring Boot)│
    └──────────────┘
           │
           │ DFC (Java)
           ▼
    ┌──────────────┐
    │  Docbroker   │
    └──────────────┘
           │
           ▼
    ┌──────────────┐
    │  Repository  │
    └──────────────┘
```

## Prerequisites

- **Java 17+** (or Java 11+ with minor pom.xml adjustments)
- **Maven 3.8+**
- **DFC installed and configured** on the target machine
  - DFC libraries (dfc.jar, etc.) must be on the classpath
  - Valid `dfc.properties` file configured for your environment

> **Note:** DFC libraries cannot be embedded due to licensing restrictions. This application expects DFC to be pre-installed on the system where it runs.

## Building

```bash
# Clone the repository
git clone https://gitea.sevenbrooms.com/spire/dfc-bridge.git
cd dfc-bridge

# Build with Maven
mvn clean package

# The JAR will be in target/dfc-bridge-1.0.0-SNAPSHOT.jar
```

## Running

### Using the Startup Script (Recommended)

The `start-dfc-bridge.sh` script provides an easy way to run DFC Bridge with or without DFC libraries.

**First, extract the JAR:**
```bash
mvn package
unzip target/dfc-bridge-*.jar -d target/extracted
```

**Run with DFC (production mode):**
```bash
./start-dfc-bridge.sh
# or explicitly:
./start-dfc-bridge.sh --mode=dfc
```

**Run without DFC (development/testing mode):**
```bash
./start-dfc-bridge.sh --mode=nodfc
```

**Specify a custom port:**
```bash
./start-dfc-bridge.sh --mode=nodfc --port=8080
```

**Show help:**
```bash
./start-dfc-bridge.sh --help
```

#### Startup Script Options

| Option | Description |
|--------|-------------|
| `--mode=dfc` | Run with DFC libraries (default) |
| `--mode=nodfc` | Run without DFC (degraded mode) |
| `--port=PORT` | Set server port (default: 9876) |
| `--help` | Show help message |

#### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DFC_HOME` | Path to DFC shared libraries | `$HOME/documentum/shared` |
| `DFC_CONFIG` | Path to DFC config directory | `$HOME/documentum/config` |
| `SERVER_PORT` | Server port | 9876 |

### Alternative: Manual Java Invocation

If you prefer not to use the startup script:

**With DFC on classpath:**
```bash
export DFC_HOME=/opt/dctm/product/7.3
export CLASSPATH=$DFC_HOME/shared/lib/dfc.jar:$DFC_HOME/shared/lib/*
java -jar target/dfc-bridge-1.0.0-SNAPSHOT.jar
```

**Using Maven (development):**
```bash
mvn spring-boot:run
```

## Configuration

Application configuration is in `src/main/resources/application.yml`:

```yaml
server:
  port: 9876

dfc:
  session:
    timeout-minutes: 30      # Session timeout
    auto-reconnect: true     # Auto-reconnect on session loss
    max-sessions-per-profile: 10
```

### Port Configuration

The default port is **9876**. Override using any of these methods:

**Command line argument:**
```bash
java -jar dfc-bridge-1.0.0-SNAPSHOT.jar --server.port=8080
```

**Environment variable:**
```bash
export SERVER_PORT=8080
java -jar dfc-bridge-1.0.0-SNAPSHOT.jar
```

**application.yml override:**
Create `application.yml` in the same directory as the JAR.

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP port | 9876 |
| `DFC_SESSION_TIMEOUT_MINUTES` | Session timeout | 30 |
| `DFC_HOME` | Path to DFC shared libraries (used by startup script) | - |
| `DFC_CONFIG` | Path to DFC config directory (used by startup script) | - |

## API Documentation

Once running, access the Swagger UI at:
- **Swagger UI:** http://localhost:9876/swagger-ui.html
- **OpenAPI JSON:** http://localhost:9876/api-docs

## API Endpoints

### Session Management

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/connect` | Establish DFC session |
| POST | `/api/v1/disconnect` | Close DFC session |
| GET | `/api/v1/session/{sessionId}` | Get session info |
| GET | `/api/v1/session/{sessionId}/valid` | Check session validity |

### DQL Queries

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/dql` | Execute DQL query |

### Object Operations

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/objects/{id}` | Get object by r_object_id |
| POST | `/api/v1/objects/{id}` | Update object attributes |
| GET | `/api/v1/folders/{path}` | List folder contents |
| GET | `/api/v1/types` | List object types |
| GET | `/api/v1/types/{typeName}` | Get type info |
| POST | `/api/v1/api` | Execute arbitrary DFC method |

## Usage Examples

### Connect to Repository

```bash
curl -X POST http://localhost:9876/api/v1/connect \
  -H "Content-Type: application/json" \
  -d '{
    "docbroker": "docbroker.example.com",
    "port": 1489,
    "repository": "MyRepo",
    "username": "dmadmin",
    "password": "password123"
  }'
```

Response:
```json
{
  "sessionId": "abc123-...",
  "repositoryInfo": {
    "name": "MyRepo",
    "serverVersion": "23.2...."
  }
}
```

### Execute DQL Query

```bash
curl -X POST http://localhost:9876/api/v1/dql \
  -H "Content-Type: application/json" \
  -d '{
    "sessionId": "abc123-...",
    "query": "SELECT r_object_id, object_name FROM dm_document WHERE FOLDER('\''/Temp'\'')",
    "maxRows": 100
  }'
```

### Get Object

```bash
curl "http://localhost:9876/api/v1/objects/0901234567890123?sessionId=abc123-..."
```

### Disconnect

```bash
curl -X POST http://localhost:9876/api/v1/disconnect \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "abc123-..."}'
```

## Health Check

```bash
curl http://localhost:9876/actuator/health
```

## Development

### Project Structure

```
dfc-bridge/
├── src/main/java/com/spire/dfcbridge/
│   ├── DfcBridgeApplication.java       # Main application
│   ├── config/                          # Configuration classes
│   ├── controller/                      # REST controllers
│   │   ├── SessionController.java
│   │   ├── DqlController.java
│   │   └── ObjectController.java
│   ├── dto/                             # Request/Response DTOs
│   ├── exception/                       # Exception handling
│   ├── model/                           # Domain models
│   └── service/                         # Business logic
│       ├── DfcSessionService.java
│       ├── DqlService.java
│       ├── ObjectService.java
│       └── impl/                        # Service implementations
├── src/main/resources/
│   └── application.yml                  # Configuration
└── pom.xml                              # Maven build file
```

### Running Tests

```bash
mvn test
```

## Graceful Degradation

The bridge can start without DFC libraries. When DFC is unavailable:

- The service starts normally and responds to health checks
- The `/api/v1/status` endpoint reports `mode: degraded`
- DFC-dependent endpoints (connect, DQL, etc.) return HTTP 503 with error code `DFC_UNAVAILABLE`
- Non-DFC endpoints continue to work

Check DFC availability:
```bash
curl http://localhost:9876/api/v1/status
```

Response when DFC unavailable:
```json
{
  "service": "dfc-bridge",
  "dfcAvailable": false,
  "mode": "degraded",
  "dfcUnavailableReason": "DFC client class not found on classpath"
}
```

## Troubleshooting

### DFC not found on classpath

Error: `DFC_UNAVAILABLE - DFC libraries are not available`

Solution: Ensure DFC JARs are on the classpath when running the application:
```bash
export CLASSPATH=$DFC_HOME/shared/lib/dfc.jar:$DFC_HOME/shared/lib/*
```

### Connection refused

Check that:
1. The docbroker host and port are correct
2. Network connectivity to the docbroker is available
3. The `dfc.properties` file is properly configured

### Session timeout

Sessions expire after the configured timeout (default: 30 minutes). Either:
- Increase `dfc.session.timeout-minutes` in configuration
- Implement session keep-alive in your client

## License

MIT License - see LICENSE file for details.

## Related Projects

- [dctm-vscode](https://gitea.sevenbrooms.com/tchristie/dctm-vscode) - VS Code extension for Documentum
