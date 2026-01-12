# DFC Bridge Deployment Guide

This guide covers deploying DFC Bridge with a full DFC installation for production use.

## Table of Contents

1. [Prerequisites](#prerequisites)
2. [DFC Installation](#dfc-installation)
3. [DFC Configuration](#dfc-configuration)
4. [Classpath Setup](#classpath-setup)
5. [Running the Application](#running-the-application)
6. [Troubleshooting](#troubleshooting)
7. [Platform-Specific Notes](#platform-specific-notes)

---

## Prerequisites

### Java

- **Java 17+** required (Java 11+ may work with pom.xml adjustments)
- Verify installation: `java -version`

### Maven (for building)

- **Maven 3.8+** required
- Verify installation: `mvn -version`

### DFC Installation

DFC libraries must be installed on the target machine. These cannot be bundled due to licensing restrictions.

Typical DFC installation locations:
- **Windows:** `C:\Documentum\shared` or `C:\Program Files\Documentum\shared`
- **Linux:** `/opt/dctm/product/<version>/shared` or `$HOME/documentum/shared`

### Environment Variables

| Variable | Description | Example |
|----------|-------------|---------|
| `DFC_HOME` | Path to DFC shared libraries | `/home/user/documentum/shared` |
| `DFC_CONFIG` | Path to DFC config (dfc.properties) | `/home/user/documentum/config` |
| `DOCUMENTUM` | Root Documentum directory (optional) | `/home/user/documentum` |
| `DOCUMENTUM_SHARED` | Shared libraries directory (optional) | `/home/user/documentum/shared` |

---

## DFC Installation

### Required JARs

DFC Bridge requires only **5 JARs** from the DFC installation:

```
$DFC_HOME/
├── dfc.jar                    # Core DFC library (17 MB)
├── aspectjrt.jar              # AspectJ runtime - required by DFC internals
├── log4j-api-2.19.0.jar       # Log4j 2 API
├── log4j-core-2.19.0.jar      # Log4j 2 Core
└── log4j-1.2-api-2.19.0.jar   # Log4j 1.x compatibility bridge
```

> **Note:** Earlier DFC versions may use different log4j JAR names (e.g., `log4j.jar`). Use whichever log4j JARs are present in your DFC installation.

### Copying from an Existing Installation

Copy only the required JARs from a Content Server or DFC client installation:

```bash
# Linux - copy only required JARs
DFC_SOURCE=/opt/dctm/product/23.2/shared
mkdir -p $HOME/documentum/shared
cp $DFC_SOURCE/dfc.jar $HOME/documentum/shared/
cp $DFC_SOURCE/aspectjrt.jar $HOME/documentum/shared/
cp $DFC_SOURCE/log4j*.jar $HOME/documentum/shared/
```

```powershell
# Windows - copy only required JARs
$DFC_SOURCE = "C:\Program Files\Documentum\shared"
mkdir C:\Documentum\shared -Force
Copy-Item "$DFC_SOURCE\dfc.jar" C:\Documentum\shared\
Copy-Item "$DFC_SOURCE\aspectjrt.jar" C:\Documentum\shared\
Copy-Item "$DFC_SOURCE\log4j*.jar" C:\Documentum\shared\
```

### Optional JARs

These JARs are **not required** for dfc-bridge but may be needed for specific features:

| JAR | When Needed |
|-----|-------------|
| `jcifs-krb5-*.jar`, `krbutil.jar` | Kerberos authentication |
| `certjFIPS.jar`, `cryptojFIPS.jar` | FIPS-compliant cryptography environments |
| `workflow.jar`, `bpmutil.jar` | Workflow operations (not exposed by dfc-bridge) |

---

## DFC Configuration

### dfc.properties

Create `dfc.properties` in your `$DFC_CONFIG` directory:

```properties
# Docbroker configuration
dfc.docbroker.host[0]=docbroker.example.com
dfc.docbroker.port[0]=1489

# Global registry (Content Server host)
dfc.globalregistry.repository=MyRepo
dfc.globalregistry.username=dm_bof_registry
dfc.globalregistry.password=

# Session configuration
dfc.session.max_count=100
dfc.session.secure_connect_default=try_native_first

# Crypto configuration (if using encrypted passwords)
dfc.crypto.repository=MyRepo

# Logging
dfc.tracing.enabled=false
dfc.tracing.timing.enabled=false
```

### Docbroker Configuration

The docbroker is the entry point to Documentum. Get these values from your Documentum administrator:

| Property | Description |
|----------|-------------|
| `dfc.docbroker.host[0]` | Docbroker hostname or IP |
| `dfc.docbroker.port[0]` | Docbroker port (default: 1489) |

Multiple docbrokers can be configured:
```properties
dfc.docbroker.host[0]=primary-docbroker.example.com
dfc.docbroker.port[0]=1489
dfc.docbroker.host[1]=backup-docbroker.example.com
dfc.docbroker.port[1]=1489
```

### dmcl.ini (Windows)

On Windows, you may also need `dmcl.ini`:

```ini
[DOCBROKER_PRIMARY]
host=docbroker.example.com
port=1489

[DMAPI_CONFIGURATION]
cache_queries=T
```

---

## Classpath Setup

### Critical: Classpath Order

DFC has specific classpath requirements. The order matters:

1. **DFC log4j JARs first** - DFC's log4j must load before Spring Boot's
2. **DFC config directory** - Contains dfc.properties
3. **DFC libraries** - All other DFC JARs
4. **Application classes** - DFC Bridge code
5. **Spring Boot libraries** - Framework dependencies

### Log4j Conflict Resolution

DFC uses log4j 1.x, while Spring Boot uses log4j 2.x via SLF4J. To avoid conflicts:

1. **Exclude `log4j-to-slf4j` from Spring Boot** - This bridge conflicts with DFC's log4j
2. **Load DFC's log4j first** - Ensure DFC's logging works correctly

The startup script handles this automatically.

### Manual Classpath Example

```bash
# DFC JARs (log4j first!)
DFC_CP="$DFC_HOME/log4j.jar:$DFC_HOME/log4j-1.2-api-2.17.1.jar"
for jar in $DFC_HOME/*.jar; do
    case "$jar" in
        *log4j*) ;;  # Skip, already added
        *) DFC_CP="$DFC_CP:$jar" ;;
    esac
done

# Full classpath
CP="$DFC_CP:$DFC_CONFIG:BOOT-INF/classes:BOOT-INF/lib/*"
```

---

## Running the Application

### Build and Extract

```bash
# Build the application
mvn clean package

# Extract the JAR (required for classpath manipulation)
cd target
unzip dfc-bridge-*.jar -d extracted
cd ..
```

### Using the Startup Script (Recommended)

```bash
# DFC mode (default)
./start-dfc-bridge.sh

# Specify DFC location
DFC_HOME=/path/to/dfc/shared ./start-dfc-bridge.sh

# Development mode (no DFC)
./start-dfc-bridge.sh --mode=nodfc

# Custom port
./start-dfc-bridge.sh --port=8080
```

### Manual Invocation

```bash
cd target/extracted

# Build classpath (DFC log4j first)
DFC_CP=""
for jar in $DFC_HOME/log4j*.jar; do
    DFC_CP="$DFC_CP:$jar"
done
for jar in $DFC_HOME/*.jar; do
    case "$jar" in *log4j*) ;; *) DFC_CP="$DFC_CP:$jar" ;; esac
done

# Build boot classpath (exclude log4j-to-slf4j)
BOOT_CP=""
for jar in BOOT-INF/lib/*.jar; do
    case "$jar" in *log4j-to-slf4j*) ;; *) BOOT_CP="$BOOT_CP:$jar" ;; esac
done

# Full classpath
CP="$DFC_CP:$DFC_CONFIG:BOOT-INF/classes:$BOOT_CP"

# Run with Java 17+ add-opens for DFC compatibility
java \
  --add-opens java.base/sun.security.x509=ALL-UNNAMED \
  --add-opens java.base/sun.security.util=ALL-UNNAMED \
  --add-opens java.base/sun.security.tools.keytool=ALL-UNNAMED \
  --add-opens java.base/sun.security.pkcs=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.security=ALL-UNNAMED \
  -cp "$CP" \
  com.spire.dfcbridge.DfcBridgeApplication
```

### Java 17+ Module Access

DFC uses reflection on internal Java classes. Java 17+ requires explicit access:

```
--add-opens java.base/sun.security.x509=ALL-UNNAMED
--add-opens java.base/sun.security.util=ALL-UNNAMED
--add-opens java.base/sun.security.tools.keytool=ALL-UNNAMED
--add-opens java.base/sun.security.pkcs=ALL-UNNAMED
--add-opens java.base/java.lang=ALL-UNNAMED
--add-opens java.base/java.util=ALL-UNNAMED
--add-opens java.base/java.io=ALL-UNNAMED
--add-opens java.base/java.security=ALL-UNNAMED
```

Without these, you'll see `InaccessibleObjectException` errors.

### Verify Startup

Check the application started correctly:

```bash
# Health check
curl http://localhost:9876/actuator/health

# DFC status
curl http://localhost:9876/api/v1/status
```

Expected response when DFC is available:
```json
{
  "service": "dfc-bridge",
  "dfcAvailable": true,
  "mode": "full"
}
```

---

## Troubleshooting

### ClassNotFoundException: com.documentum.fc.client.DfClient

**Cause:** DFC JARs not on classpath

**Solution:**
1. Verify `$DFC_HOME` points to directory containing `dfc.jar`
2. Check the startup script is building the classpath correctly
3. Run in no-DFC mode to verify the app itself works: `./start-dfc-bridge.sh --mode=nodfc`

### NoClassDefFoundError: org/apache/log4j/Logger

**Cause:** Log4j conflict between DFC and Spring Boot

**Solution:**
1. Ensure DFC's log4j JARs are loaded FIRST in the classpath
2. Exclude `log4j-to-slf4j` from the Spring Boot classpath
3. Use the provided startup script which handles this

### InaccessibleObjectException / IllegalAccessError

**Cause:** Java 17+ module system blocking DFC's reflection

**Solution:** Add the `--add-opens` flags listed above

### DFC_UNAVAILABLE - DFC libraries are not available

**Cause:** Application started but DFC classes not found

**Solution:**
1. Check the `/api/v1/status` endpoint for details
2. Verify DFC JARs are on classpath
3. Check startup logs for classpath information

### Connection refused to docbroker

**Cause:** Cannot reach the docbroker

**Solution:**
1. Verify docbroker host and port in `dfc.properties`
2. Test network connectivity: `telnet docbroker.example.com 1489`
3. Check firewall rules
4. Verify docbroker is running on the Content Server

### DM_SESSION_E_AUTH_FAIL

**Cause:** Invalid credentials

**Solution:**
1. Verify username and password
2. Check if user account is locked
3. Verify user has access to the repository

### Session timeout errors

**Cause:** DFC sessions expiring

**Solution:**
1. Increase timeout in `application.yml`:
   ```yaml
   dfc:
     session:
       timeout-minutes: 60
   ```
2. Implement keep-alive in your client
3. Handle session expiry and reconnect

---

## Platform-Specific Notes

### Windows

1. **Path separators:** Use semicolons (`;`) in CLASSPATH, not colons
2. **dmcl.ini:** May be required in `%DOCUMENTUM%\config`
3. **Environment variables:** Set via System Properties or batch file
4. **Service deployment:** Use NSSM or Windows Service wrapper

Example Windows batch file:
```batch
@echo off
set DFC_HOME=C:\Documentum\shared
set DFC_CONFIG=C:\Documentum\config
set CLASSPATH=%DFC_HOME%\dfc.jar;%DFC_HOME%\*

java -jar dfc-bridge.jar
```

### Linux / WSL

1. **Path separators:** Use colons (`:`) in CLASSPATH
2. **Permissions:** Ensure execute permission on startup script
3. **systemd:** Create a service unit for production deployment

Example systemd unit (`/etc/systemd/system/dfc-bridge.service`):
```ini
[Unit]
Description=DFC Bridge Service
After=network.target

[Service]
Type=simple
User=dctm
Environment="DFC_HOME=/opt/dctm/shared"
Environment="DFC_CONFIG=/opt/dctm/config"
WorkingDirectory=/opt/dfc-bridge
ExecStart=/opt/dfc-bridge/start-dfc-bridge.sh
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

### Docker

For containerized deployment:

1. **Base image:** Use a JDK 17+ image
2. **DFC libraries:** Mount or copy DFC JARs into the container
3. **Configuration:** Mount dfc.properties as a volume

Example Dockerfile:
```dockerfile
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy application
COPY target/extracted/ /app/

# DFC libraries must be provided at runtime via volume mount
# docker run -v /path/to/dfc:/dfc ...

ENV DFC_HOME=/dfc/shared
ENV DFC_CONFIG=/dfc/config
ENV SERVER_PORT=9876

EXPOSE 9876

COPY start-dfc-bridge.sh /app/
RUN chmod +x /app/start-dfc-bridge.sh

ENTRYPOINT ["/app/start-dfc-bridge.sh"]
```

---

## Quick Reference

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DFC_HOME` | Yes (DFC mode) | `$HOME/documentum/shared` | DFC shared libraries |
| `DFC_CONFIG` | Yes (DFC mode) | `$HOME/documentum/config` | DFC configuration |
| `SERVER_PORT` | No | `9876` | HTTP server port |

### Startup Script Options

| Option | Description |
|--------|-------------|
| `--mode=dfc` | Run with DFC (default) |
| `--mode=nodfc` | Run without DFC (development) |
| `--port=PORT` | Set server port |
| `--help` | Show help |

### Health Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Spring Boot health check |
| `/api/v1/status` | DFC availability status |
