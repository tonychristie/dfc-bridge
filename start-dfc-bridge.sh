#!/bin/bash
cd /home/tchristie/projects/dfc-bridge/target/extracted

DFC_HOME=/home/tchristie/documentum/shared
DFC_CONFIG=/home/tchristie/documentum/config

# Build DFC classpath (DFC log4j jars MUST come first to avoid conflicts)
DFC_CP=""
for jar in $DFC_HOME/log4j*.jar; do
    if [ -z "$DFC_CP" ]; then
        DFC_CP="$jar"
    else
        DFC_CP="$DFC_CP:$jar"
    fi
done
for jar in $DFC_HOME/*.jar; do
    case "$jar" in
        *log4j*) ;; # Skip, already added
        *)
            if [ -z "$DFC_CP" ]; then
                DFC_CP="$jar"
            else
                DFC_CP="$DFC_CP:$jar"
            fi
            ;;
    esac
done

# Build Spring Boot lib classpath (exclude log4j-to-slf4j bridge to avoid conflict)
BOOT_CP=""
for jar in BOOT-INF/lib/*.jar; do
    case "$jar" in
        *log4j-to-slf4j*) ;; # Skip this conflicting bridge
        *)
            if [ -z "$BOOT_CP" ]; then
                BOOT_CP="$jar"
            else
                BOOT_CP="$BOOT_CP:$jar"
            fi
            ;;
    esac
done

# Full classpath - DFC first, then config, then app classes, then boot libs
CP="$DFC_CP:$DFC_CONFIG:BOOT-INF/classes:$BOOT_CP"

echo "Starting DFC Bridge..."
echo "Classpath: $CP" | head -c 500
echo "..."
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
