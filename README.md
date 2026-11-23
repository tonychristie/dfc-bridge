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

- **Java 17+**
- **Maven 3.8+**
- **DFC JARs** from a Documentum installation (only 5 JARs required):
  - `dfc.jar` - Core DFC library
  - `aspectjrt.jar` - AspectJ runtime
  - `log4j-api-*.jar`, `log4j-core-*.jar`, `log4j-1.2-api-*.jar` - Logging
- **dfc.properties** file with your DFC settings. It does not choose the docbroker: each connect request names the docbroker and port to connect through

> **Note:** DFC libraries cannot be embedded due to licensing restrictions. Copy only the required JARs from an existing Documentum installation. See [DEPLOYMENT.md](DEPLOYMENT.md) for detailed setup instructions.

## Building

```bash
# Build with Maven
mvn clean package

# The JAR will be in target/dfc-bridge-1.2.0.jar
```

## Running

### Using the Startup Script (Recommended)

The `start-dfc-bridge.sh` script provides an easy way to run DFC Bridge with or without DFC libraries.

**First, build the JAR:**
```bash
mvn package
```

On start, the script extracts the JAR into `run/extracted` when the JAR has changed since the last start. The bridge writes its log to `run/logs`.

The script also runs from the unzipped release bundle, where it uses the JAR beside it.

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
| `--verify` | Verify DFC setup without starting |
| `--help` | Show help message |

#### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `DFC_HOME` | Path to DFC shared libraries, passed to the bridge as `--dfc.home` | `$HOME/documentum/shared` |
| `DFC_CONFIG` | Path to DFC config directory, passed to the bridge as `--dfc.config` | `$HOME/documentum/config` |
| `SERVER_PORT` | Server port | 9876 |
| `SERVER_ADDRESS` | Address the server listens on | 127.0.0.1 |

#### On Windows

Run `start-dfc-bridge.cmd` from a Command Prompt or PowerShell window. It takes the same options and environment variables as `start-dfc-bridge.sh`.

```bat
start-dfc-bridge.cmd
start-dfc-bridge.cmd --mode=nodfc --port=8080
```

To set an environment variable, use `set DFC_HOME=C:\Documentum\shared` in a Command Prompt or `$env:DFC_HOME = "C:\Documentum\shared"` in PowerShell.

### Alternative: Manual Java Invocation

If you prefer not to use the startup script:

**With DFC:** see [Manual Invocation](DEPLOYMENT.md#manual-invocation) in `DEPLOYMENT.md`.

**Using Maven (development):**
```bash
mvn spring-boot:run
```

## Configuration

Application configuration is in `src/main/resources/application.yml`:

```yaml
server:
  address: 127.0.0.1
  port: 9876

dfc:
  session:
    timeout-minutes: 30      # Session timeout
```

### Listening Address

The bridge listens on **127.0.0.1** only, so only the local machine can reach it. The bridge has no authentication: any client that can reach it can open a session and run any DFC method or dmAPI command as the connected user.

To accept connections from other hosts, set the address to `0.0.0.0`:

**With the startup script:**
```bash
SERVER_ADDRESS=0.0.0.0 ./start-dfc-bridge.sh
```

**With `java -jar`:**
```bash
java -jar dfc-bridge-1.2.0.jar --server.address=0.0.0.0
```

### Port Configuration

The default port is **9876**. Override using any of these methods:

**Command line argument:**
```bash
java -jar dfc-bridge-1.2.0.jar --server.port=8080
```

**Environment variable:**
```bash
export SERVER_PORT=8080
java -jar dfc-bridge-1.2.0.jar
```

**application.yml override:**
Create `application.yml` in the same directory as the JAR.

### Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP port | 9876 |
| `SERVER_ADDRESS` | Address the server listens on | 127.0.0.1 |
| `DFC_SESSION_TIMEOUT_MINUTES` | Session timeout | 30 |
| `DFC_HOME` | Path to DFC shared libraries (used by startup script, which passes it as `--dfc.home`) | - |
| `DFC_CONFIG` | Path to DFC config directory (used by startup script, which passes it as `--dfc.config`) | - |

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
  "dfcUnavailableReason": "dfc.home is not set"
}
```

## Troubleshooting

### DFC not found

Error: `DFC_UNAVAILABLE - DFC libraries are not available`. `/api/v1/status` gives the reason: `dfc.home is not set`, or `dfc.jar not found in <path>`.

Solution: Set `DFC_HOME` to the directory that holds the DFC JARs and start with `./start-dfc-bridge.sh`, or follow [Manual Invocation](DEPLOYMENT.md#manual-invocation) in `DEPLOYMENT.md`. Run `./start-dfc-bridge.sh --verify` to check the setup.

### Connection refused

Check that:
1. The `docbroker` and `port` in the connect request are correct. They choose the docbroker; `dfc.properties` does not
2. Network connectivity to the docbroker is available

### Session timeout

Sessions expire after the configured timeout (default: 30 minutes). Either:
- Increase `dfc.session.timeout-minutes` in configuration
- Implement session keep-alive in your client

## License

MIT License - see LICENSE file for details.

## Related Projects

- dctm-vscode - VS Code extension for Documentum
