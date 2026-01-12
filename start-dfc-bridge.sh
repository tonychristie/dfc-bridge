#!/bin/bash
#
# DFC Bridge Startup Script
# Supports two modes:
#   --mode=dfc   : Full DFC mode with Documentum libraries (default)
#   --mode=nodfc : Development mode without DFC dependencies
#
# Usage:
#   ./start-dfc-bridge.sh              # DFC mode (default)
#   ./start-dfc-bridge.sh --mode=dfc   # Explicit DFC mode
#   ./start-dfc-bridge.sh --mode=nodfc # No-DFC development mode
#   ./start-dfc-bridge.sh --help       # Show help
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EXTRACTED_DIR="$SCRIPT_DIR/target/extracted"

# Defaults
MODE="dfc"
DFC_HOME="${DFC_HOME:-$HOME/documentum/shared}"
DFC_CONFIG="${DFC_CONFIG:-$HOME/documentum/config}"
SERVER_PORT="${SERVER_PORT:-9876}"

# Required JARs for DFC mode
REQUIRED_JARS=("dfc.jar" "aspectjrt.jar")
REQUIRED_LOG4J_PATTERN="log4j"

# Parse command line arguments
show_help() {
    echo "DFC Bridge Startup Script"
    echo ""
    echo "Usage: $0 [OPTIONS]"
    echo ""
    echo "Options:"
    echo "  --mode=dfc     Run with DFC libraries (default)"
    echo "  --mode=nodfc   Run without DFC libraries (development/testing mode)"
    echo "  --port=PORT    Set server port (default: 9876, or \$SERVER_PORT)"
    echo "  --verify       Verify DFC setup without starting the application"
    echo "  --help         Show this help message"
    echo ""
    echo "Environment Variables:"
    echo "  DFC_HOME       Path to DFC shared libraries (default: \$HOME/documentum/shared)"
    echo "  DFC_CONFIG     Path to DFC config directory (default: \$HOME/documentum/config)"
    echo "  SERVER_PORT    Server port (default: 9876)"
    echo ""
    echo "Required JARs in DFC_HOME:"
    echo "  - dfc.jar              Core DFC library"
    echo "  - aspectjrt.jar        AspectJ runtime"
    echo "  - log4j*.jar           Log4j libraries (api, core, 1.2-api)"
    echo ""
    echo "Examples:"
    echo "  $0                       # Start with DFC (production)"
    echo "  $0 --verify              # Check DFC setup"
    echo "  $0 --mode=nodfc          # Start without DFC (development)"
    echo "  $0 --mode=nodfc --port=8080  # Development mode on port 8080"
    echo ""
}

# Verify DFC setup
verify_dfc_setup() {
    local errors=0
    echo "Verifying DFC setup..."
    echo ""
    echo "DFC_HOME: $DFC_HOME"
    echo "DFC_CONFIG: $DFC_CONFIG"
    echo ""

    # Check DFC_HOME exists
    if [ ! -d "$DFC_HOME" ]; then
        echo "[ERROR] DFC_HOME directory not found: $DFC_HOME"
        return 1
    fi

    # Check required JARs
    echo "Checking required JARs:"
    for jar in "${REQUIRED_JARS[@]}"; do
        if [ -f "$DFC_HOME/$jar" ]; then
            local size=$(du -h "$DFC_HOME/$jar" | cut -f1)
            echo "  [OK] $jar ($size)"
        else
            echo "  [MISSING] $jar"
            ((errors++))
        fi
    done

    # Check log4j JARs
    local log4j_count=$(ls "$DFC_HOME"/$REQUIRED_LOG4J_PATTERN*.jar 2>/dev/null | wc -l)
    if [ "$log4j_count" -gt 0 ]; then
        echo "  [OK] log4j JARs found ($log4j_count files):"
        for jar in "$DFC_HOME"/$REQUIRED_LOG4J_PATTERN*.jar; do
            [ -e "$jar" ] || continue
            echo "       - $(basename "$jar")"
        done
    else
        echo "  [MISSING] No log4j*.jar files found"
        ((errors++))
    fi

    echo ""

    # Check DFC_CONFIG
    if [ ! -d "$DFC_CONFIG" ]; then
        echo "[ERROR] DFC_CONFIG directory not found: $DFC_CONFIG"
        ((errors++))
    elif [ ! -f "$DFC_CONFIG/dfc.properties" ]; then
        echo "[WARNING] dfc.properties not found in $DFC_CONFIG"
        echo "  You may need to create this file with docbroker settings."
    else
        echo "[OK] dfc.properties found"
        # Extract docbroker info
        local docbroker=$(grep -E '^dfc.docbroker.host\[0\]' "$DFC_CONFIG/dfc.properties" 2>/dev/null | cut -d= -f2)
        if [ -n "$docbroker" ]; then
            echo "     Docbroker: $docbroker"
        fi
    fi

    echo ""
    if [ $errors -eq 0 ]; then
        echo "DFC setup verification: PASSED"
        echo "You can start the application with: $0"
        return 0
    else
        echo "DFC setup verification: FAILED ($errors error(s))"
        echo ""
        echo "To fix, copy the required JARs from a Documentum installation:"
        echo "  cp /path/to/documentum/shared/dfc.jar $DFC_HOME/"
        echo "  cp /path/to/documentum/shared/aspectjrt.jar $DFC_HOME/"
        echo "  cp /path/to/documentum/shared/log4j*.jar $DFC_HOME/"
        return 1
    fi
}

VERIFY_ONLY=false

for arg in "$@"; do
    case $arg in
        --mode=*)
            MODE="${arg#*=}"
            ;;
        --port=*)
            SERVER_PORT="${arg#*=}"
            ;;
        --verify)
            VERIFY_ONLY=true
            ;;
        --help|-h)
            show_help
            exit 0
            ;;
        *)
            echo "Unknown option: $arg"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Handle verify-only mode
if [ "$VERIFY_ONLY" = true ]; then
    verify_dfc_setup
    exit $?
fi

# Validate mode
if [[ "$MODE" != "dfc" && "$MODE" != "nodfc" ]]; then
    echo "Error: Invalid mode '$MODE'. Must be 'dfc' or 'nodfc'"
    exit 1
fi

# Check extracted directory exists
if [ ! -d "$EXTRACTED_DIR" ]; then
    echo "Error: Extracted JAR directory not found at $EXTRACTED_DIR"
    echo "Run: mvn package && cd target && unzip dfc-bridge-*.jar -d extracted"
    exit 1
fi

cd "$EXTRACTED_DIR"

# Build Spring Boot lib classpath (exclude log4j-to-slf4j bridge to avoid conflict with DFC)
build_boot_classpath() {
    local BOOT_CP=""
    for jar in BOOT-INF/lib/*.jar; do
        case "$jar" in
            *log4j-to-slf4j*)
                # Skip this conflicting bridge when in DFC mode
                if [[ "$MODE" == "dfc" ]]; then
                    continue
                fi
                ;;
        esac
        if [ -z "$BOOT_CP" ]; then
            BOOT_CP="$jar"
        else
            BOOT_CP="$BOOT_CP:$jar"
        fi
    done
    echo "$BOOT_CP"
}

# Build DFC classpath (DFC log4j jars MUST come first to avoid conflicts)
build_dfc_classpath() {
    local DFC_CP=""

    # Add log4j jars first
    for jar in "$DFC_HOME"/log4j*.jar; do
        [ -e "$jar" ] || continue
        if [ -z "$DFC_CP" ]; then
            DFC_CP="$jar"
        else
            DFC_CP="$DFC_CP:$jar"
        fi
    done

    # Add remaining DFC jars (skip log4j, already added)
    for jar in "$DFC_HOME"/*.jar; do
        [ -e "$jar" ] || continue
        case "$jar" in
            *log4j*) continue ;;
        esac
        if [ -z "$DFC_CP" ]; then
            DFC_CP="$jar"
        else
            DFC_CP="$DFC_CP:$jar"
        fi
    done

    echo "$DFC_CP"
}

# JVM options for DFC compatibility (required for Java 17+)
JVM_OPTS="--add-opens java.base/sun.security.x509=ALL-UNNAMED \
  --add-opens java.base/sun.security.util=ALL-UNNAMED \
  --add-opens java.base/sun.security.tools.keytool=ALL-UNNAMED \
  --add-opens java.base/sun.security.pkcs=ALL-UNNAMED \
  --add-opens java.base/java.lang=ALL-UNNAMED \
  --add-opens java.base/java.util=ALL-UNNAMED \
  --add-opens java.base/java.io=ALL-UNNAMED \
  --add-opens java.base/java.security=ALL-UNNAMED"

# Build classpath based on mode
BOOT_CP=$(build_boot_classpath)

if [[ "$MODE" == "dfc" ]]; then
    # DFC mode: DFC jars first, then config, then app classes, then boot libs
    if [ ! -d "$DFC_HOME" ]; then
        echo "Error: DFC_HOME directory not found at $DFC_HOME"
        echo "Either set DFC_HOME environment variable or use --mode=nodfc"
        exit 1
    fi

    DFC_CP=$(build_dfc_classpath)
    CP="$DFC_CP:$DFC_CONFIG:BOOT-INF/classes:$BOOT_CP"

    echo "Starting DFC Bridge in DFC mode..."
    echo "  DFC_HOME: $DFC_HOME"
    echo "  Port: $SERVER_PORT"
else
    # No-DFC mode: just app classes and boot libs
    CP="BOOT-INF/classes:$BOOT_CP"

    echo "Starting DFC Bridge in NO-DFC mode (development/testing)..."
    echo "  Port: $SERVER_PORT"
    echo "  Note: DFC operations will return 503 Service Unavailable"
fi

echo ""

# Run the application
exec java \
  $JVM_OPTS \
  -Dserver.port="$SERVER_PORT" \
  -cp "$CP" \
  com.spire.dfcbridge.DfcBridgeApplication
