#!/usr/bin/env bash

set -euo pipefail

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO] $(date +%Y-%m-%d %H:%M:%S)${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN] $(date +%Y-%m-%d %H:%M:%S)${NC} $1"; }
log_error() { echo -e "${RED}[ERROR] $(date +%Y-%m-%d %H:%M:%S)${NC} $1"; }

WORKFLOW_FILE="${WORKFLOW_FILE:-.github/workflows/ci.yml}"
COMPOSE_FILE="${COMPOSE_FILE:-app/docker-compose.yml}"
GITIGNORE=".gitignore"
GITIGNORE_BAK=".gitignore.buildsh.bak"
WORKFLOW_BAK="${WORKFLOW_FILE}.buildsh.bak"

# --- Step 1: Prerequisites ---
log_info "Verifying local CI prerequisites (java 25, pnpm, docker, wrkflw)..."

if ! command -v java &> /dev/null; then
    log_error "Java is not installed."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | grep -oE "[0-9]+" | head -1)
if [ "$JAVA_VERSION" -lt 25 ]; then
    log_error "Found Java $JAVA_VERSION. cbs-nova requires JDK 25."
    exit 1
fi

if ! command -v pnpm &> /dev/null; then
    log_error "pnpm missing. Install pnpm 9.x."
    exit 1
fi

if ! command -v docker &> /dev/null || ! docker compose version &> /dev/null; then
    log_error "Docker or Docker Compose v2 missing."
    exit 1
fi

if ! command -v wrkflw &> /dev/null; then
    log_error "wrkflw missing. Install: cargo install wrkflw"
    exit 1
fi

if [ ! -f "$WORKFLOW_FILE" ]; then
    log_error "Workflow file not found: $WORKFLOW_FILE"
    exit 1
fi

# --- Step 2: Stop interfering dev processes ---
log_info "Stopping any local Gradle/Node dev servers that could interfere..."
pkill -9 -f "Gradle|gradlew" 2> /dev/null || true
pkill -9 -f "pnpm run dev|nuxt\\.mjs dev" 2> /dev/null || true
sleep 2

# --- Step 3: Start infrastructure ---
log_info "Starting Postgres + Temporal from ${COMPOSE_FILE}..."
docker compose -f "$COMPOSE_FILE" down -v 2> /dev/null || true
docker compose -f "$COMPOSE_FILE" up -d postgres temporal

log_info "Waiting for Temporal gRPC on localhost:7233..."
for i in {1..60}; do
    if bash -c "exec 3<>/dev/tcp/127.0.0.1/7233" 2> /dev/null; then
        log_info "Temporal reachable."
        break
    fi
    sleep 2
done
if ! bash -c "exec 3<>/dev/tcp/127.0.0.1/7233" 2> /dev/null; then
    log_error "Temporal did not become reachable. Check docker compose -f ${COMPOSE_FILE} logs temporal."
    exit 1
fi

# --- Step 4: Validate workflow ---
log_info "Validating workflow ${WORKFLOW_FILE}..."
wrkflw validate "$WORKFLOW_FILE"

# --- Step 5: Work around wrkflw .gitignore bug ---
log_info "Backing up ${GITIGNORE} and neutralizing wrkflw-hostile ignore rules..."
cp "$GITIGNORE" "$GITIGNORE_BAK"
sed -i "s|^\\\\*.jar$|# *.jar|" "$GITIGNORE"
sed -i "s|^/gradle/wrapper/gradle-wrapper.jar$|# /gradle/wrapper/gradle-wrapper.jar|" "$GITIGNORE"
sed -i "s|^/gradle/wrapper/gradle-wrapper.properties$|# /gradle/wrapper/gradle-wrapper.properties|" "$GITIGNORE"
sed -i "s|^/gradlew$|# /gradlew|" "$GITIGNORE"
sed -i "s|^/gradlew.bat$|# /gradlew.bat|" "$GITIGNORE"
sed -i "s|^/build.gradle$|# /build.gradle|" "$GITIGNORE"
printf "%s\\n" "!backend/gradle/wrapper/gradle-wrapper.jar" >> "$GITIGNORE"

# --- Step 6: Work around stale copied build artifacts ---
log_info "Backing up ${WORKFLOW_FILE} and injecting ./gradlew clean ... for local run..."
cp "$WORKFLOW_FILE" "$WORKFLOW_BAK"
sed -i "s|./gradlew build --no-daemon|./gradlew clean build --no-daemon|" "$WORKFLOW_FILE"
sed -i "s|./gradlew test --no-daemon|./gradlew clean test --no-daemon|" "$WORKFLOW_FILE"

# --- Step 7: Run workflow locally ---
log_info "Running workflow locally with wrkflw..."
RUNTIME="${WRKFLW_RUNTIME:-emulation}"
wrkflw run --runtime "$RUNTIME" "$WORKFLOW_FILE" ${WRKFLW_FLAGS:-}

# --- Step 8: Restore backups ---
log_info "Restoring ${GITIGNORE} and ${WORKFLOW_FILE}..."
mv "$GITIGNORE_BAK" "$GITIGNORE"
mv "$WORKFLOW_BAK" "$WORKFLOW_FILE"

log_info "Build successful! All workflow jobs passed."
echo -e "\n=========================================================="
echo -e "  Workflow: ${GREEN}${WORKFLOW_FILE}${NC}"
echo -e "  Runtime:  ${GREEN}${RUNTIME}${NC}"
echo -e "  To re-run: ${GREEN}./build.sh${NC}"
echo -e "  To use a different runtime: ${GREEN}WRKFLW_RUNTIME=docker ./build.sh${NC}"
echo -e "==========================================================\n"
