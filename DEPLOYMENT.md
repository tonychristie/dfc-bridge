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

- **Java 17+** required
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

The docbroker is the entry point to Documentum. The connect request chooses it: the `docbroker` and `port` of `POST /api/v1/connect` decide which environment the session connects to. The `dfc.docbroker.*` entries of `dfc.properties` are not used to route a connect, and a docbroker that is in no `dfc.properties` can be used.

The bridge holds one DFC instance per docbroker host and port. Each instance gets its own `dfc.properties`, which lists only that docbroker, so one bridge can hold sessions to several environments at the same time, including two environments with the same repository name and ID. The host is used as given: `cs23` and `192.168.0.110` are two instances, even when they name the same docbroker.

---

## Classpath Setup

### DFC Is Not on the Classpath

The classpath holds only the bridge classes and the Spring Boot libraries. The bridge loads DFC itself, once per docbroker, from two arguments:

| Argument | Description |
|----------|-------------|
| `--dfc.home` | The folder that holds the DFC JARs (`DFC_HOME`) |
| `--dfc.config` | The folder that holds `dfc.properties` and `dfc.keystore` (`DFC_CONFIG`) |

The startup scripts pass both in DFC mode and neither in no-DFC mode. Without `--dfc.home`, or when it holds no `dfc.jar`, the bridge starts in degraded mode.

Each DFC instance has its own classloader over every JAR in `--dfc.home` except `servlet-api*.jar`. The `servlet-api.jar` in a Documentum `shared` folder is an older Servlet API than the one the embedded Tomcat needs. DFC's log4j is only inside these classloaders, so it does not conflict with Spring Boot's logging.

### The dfc-instances Folder

For each new docbroker host and port, the bridge writes `dfc-instances/<host>_<port>/` in its working directory (`run/` under the startup scripts):

- `config/dfc.properties`: every line of `<dfc.config>/dfc.properties`, with each `#include` line replaced by the lines of the included file, except the indexed docbroker entries (such as `dfc.docbroker.host[0]` and `dfc.docbroker.port[0]`) and `dfc.data.dir`. Global docbroker settings such as `dfc.docbroker.search_order` are kept. The requested docbroker and `dfc.data.dir` set to the `data` folder are added at the end. A missing or unreadable include is logged and skipped
- `config/`: a copy of every other file of `<dfc.config>`, such as `dfc.keystore` and `log4j2.properties`
- `data/`: the DFC data folder of that instance

In the folder name, any character of the host that is not a letter, a digit, `.` or `-` becomes `_`. The `config` folder is written again the first time the bridge connects through that docbroker after a start, so a change to `<dfc.config>` takes effect on the next start. The `data` folder is kept between restarts. An instance stays loaded until the bridge stops, including one created by a connect that failed.

---

## Running the Application

### Build

```bash
mvn clean package
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

On Windows, from a Command Prompt:

```bat
:: DFC mode (default)
start-dfc-bridge.cmd

:: Specify DFC location
set DFC_HOME=C:\path\to\dfc\shared
start-dfc-bridge.cmd

:: Development mode (no DFC)
start-dfc-bridge.cmd --mode=nodfc

:: Custom port
start-dfc-bridge.cmd --port=8080
```

### Manual Invocation

The bridge writes `logs/` and `dfc-instances/` under its working directory.

```bash
unzip dfc-bridge-*.jar -d <directory>
cd <directory>

# Classpath: the bridge classes and the Spring Boot libraries, no DFC
CP="BOOT-INF/classes"
for jar in BOOT-INF/lib/*.jar; do
    CP="$CP:$jar"
done

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
  com.spirecentral.dfcbridge.DfcBridgeApplication \
  --dfc.home="$DFC_HOME" \
  --dfc.config="$DFC_CONFIG"
```

Pass `--dfc.home` and `--dfc.config` as absolute paths, or as paths relative to the working directory.

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

**Cause:** `--dfc.home` holds `dfc.jar`, but not every JAR DFC needs. Connect returns 503 with `DFC classes not found in dfc.home`.

**Solution:**
1. Verify `$DFC_HOME` holds the required JARs listed under [Required JARs](#required-jars)
2. Run `./start-dfc-bridge.sh --verify` to check the setup
3. Run in no-DFC mode to verify the app itself works: `./start-dfc-bridge.sh --mode=nodfc`

### NoClassDefFoundError: org/apache/log4j/Logger

**Cause:** The log4j JARs are missing from `--dfc.home`. DFC loads log4j from its own classloader, which holds only the JARs in `--dfc.home`.

**Solution:**
1. Copy the log4j JARs of the DFC installation into `$DFC_HOME`
2. Run `./start-dfc-bridge.sh --verify` to check that they are found

### InaccessibleObjectException / IllegalAccessError

**Cause:** Java 17+ module system blocking DFC's reflection

**Solution:** Add the `--add-opens` flags listed above

### DFC_UNAVAILABLE - DFC libraries are not available

**Cause:** The bridge started in degraded mode: `--dfc.home` is not set, or it holds no `dfc.jar`

**Solution:**
1. Check the `/api/v1/status` endpoint for the reason (`dfc.home is not set`, or `dfc.jar not found in <path>`)
2. Start with the startup script in DFC mode, which passes `--dfc.home`, and verify `DFC_HOME` holds `dfc.jar`
3. Check the startup log for the DFC location

### Connection refused to docbroker

**Cause:** Cannot reach the docbroker

**Solution:**
1. Verify the `docbroker` and `port` in the connect request
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
2. **Environment variables:** Set via System Properties or batch file
3. **Service deployment:** Use NSSM or Windows Service wrapper
4. **Startup script:** `start-dfc-bridge.cmd` starts the bridge. `start-dfc-bridge.ps1` must stay in the same folder as it

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

---

## Quick Reference

### Environment Variables

| Variable | Required | Default | Description |
|----------|----------|---------|-------------|
| `DFC_HOME` | Yes (DFC mode) | `$HOME/documentum/shared` | DFC shared libraries |
| `DFC_CONFIG` | Yes (DFC mode) | `$HOME/documentum/config` | DFC configuration |
| `SERVER_PORT` | No | `9876` | HTTP server port |
| `SERVER_ADDRESS` | No | `127.0.0.1` | Address the server listens on |
| `DFC_SESSION_TIMEOUT_MINUTES` | No | `30` | Session timeout |

### Startup Script Options

| Option | Description |
|--------|-------------|
| `--mode=dfc` | Run with DFC (default) |
| `--mode=nodfc` | Run without DFC (development) |
| `--port=PORT` | Set server port |
| `--verify` | Verify DFC setup without starting |
| `--help` | Show help |

### Health Endpoints

| Endpoint | Description |
|----------|-------------|
| `/actuator/health` | Spring Boot health check |
| `/api/v1/status` | DFC availability status |
