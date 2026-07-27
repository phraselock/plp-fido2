#!/bin/bash
# plp-fido2 installer
# Downloads the latest release from GitHub and installs it as a systemd service.
#
# Usage:
#   curl -sSL https://raw.githubusercontent.com/phraselock/plp-fido2/main/install.sh | sudo bash
#
set -euo pipefail

GITHUB_REPO="phraselock/plp-fido2"
SERVICE_NAME="plp-fido2"
INSTALL_DIR="/opt/phraselock/fido2"
SERVICE_USER="phraselock"

# ---------------------------------------------------------------------------
# Root check
# ---------------------------------------------------------------------------
if [[ "$(id -u)" -ne 0 ]]; then
  echo "Error: this installer must run as root (sudo)." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Dialog tool (whiptail on Debian/Ubuntu, dialog on macOS for local testing)
# ---------------------------------------------------------------------------
DIALOG=$(command -v whiptail 2>/dev/null || command -v dialog 2>/dev/null || true)
if [[ -z "$DIALOG" ]]; then
  echo "Installing whiptail..." >&2
  DEBIAN_FRONTEND=noninteractive apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq whiptail
  DIALOG=$(command -v whiptail)
fi

# ---------------------------------------------------------------------------
# curl (needed for GitHub API and download)
# ---------------------------------------------------------------------------
if ! command -v curl >/dev/null 2>&1; then
  DEBIAN_FRONTEND=noninteractive apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq curl
fi

# ---------------------------------------------------------------------------
# Fetch latest release info from GitHub
# ---------------------------------------------------------------------------
echo "Fetching latest release from GitHub (${GITHUB_REPO})..."
RELEASE_JSON=$(curl -fsSL "https://api.github.com/repos/${GITHUB_REPO}/releases/latest")
VERSION=$(echo "$RELEASE_JSON" \
  | grep '"tag_name"' \
  | sed -E 's/.*"tag_name"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/')
JAR_URL=$(echo "$RELEASE_JSON" \
  | grep '"browser_download_url"' \
  | grep '\.jar"' \
  | sed -E 's/.*"browser_download_url"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/')

if [[ -z "$VERSION" || -z "$JAR_URL" ]]; then
  echo "Error: could not parse GitHub release info. Check your internet connection." >&2
  exit 1
fi

JAR_NAME=$(basename "$JAR_URL")

# ---------------------------------------------------------------------------
# Read existing config values (upgrade-friendly — already-set values become
# the defaults so a repeat run doesn't wipe configuration)
# ---------------------------------------------------------------------------
EXISTING_PORT="8081"
EXISTING_PREFIX="/webauthn"
EXISTING_IPS="127.0.0.1,::1,[0:0:0:0:0:0:0:1]"
EXISTING_MIN_THREADS="2"
EXISTING_MAX_THREADS="10"
EXISTING_TOKEN=""

if [[ -f "$INSTALL_DIR/application.properties" ]]; then
  _get() { grep "^${1}=" "$INSTALL_DIR/application.properties" 2>/dev/null | cut -d= -f2- || true; }
  EXISTING_PORT=$(_get server.port || echo "$EXISTING_PORT")
  EXISTING_PREFIX=$(_get app.path.prefix || echo "$EXISTING_PREFIX")
  EXISTING_IPS=$(_get allowed.ips || echo "$EXISTING_IPS")
  EXISTING_MIN_THREADS=$(_get jetty.minThreads || echo "$EXISTING_MIN_THREADS")
  EXISTING_MAX_THREADS=$(_get jetty.maxThreads || echo "$EXISTING_MAX_THREADS")
  EXISTING_TOKEN=$(_get admin.token || echo "")
fi

# ---------------------------------------------------------------------------
# Interactive configuration
# ---------------------------------------------------------------------------
if ! PORT=$("$DIALOG" --title "plp-fido2 ${VERSION} Setup" \
    --inputbox "HTTP port for plp-fido2:" 10 55 "$EXISTING_PORT" \
    3>&1 1>&2 2>&3); then
  echo "Aborted." >&2; exit 1
fi

if ! PREFIX=$("$DIALOG" --title "plp-fido2 ${VERSION} Setup" \
    --inputbox "nginx location prefix (e.g. /webauthn):" 10 55 "$EXISTING_PREFIX" \
    3>&1 1>&2 2>&3); then
  echo "Aborted." >&2; exit 1
fi

if ! ALLOWED_IPS=$("$DIALOG" --title "plp-fido2 ${VERSION} Setup" \
    --inputbox "Allowed IPs (comma-separated):" 10 70 "$EXISTING_IPS" \
    3>&1 1>&2 2>&3); then
  echo "Aborted." >&2; exit 1
fi

if ! MAX_THREADS=$("$DIALOG" --title "plp-fido2 ${VERSION} Setup" \
    --inputbox "Jetty max threads (concurrent requests):" 10 55 "$EXISTING_MAX_THREADS" \
    3>&1 1>&2 2>&3); then
  echo "Aborted." >&2; exit 1
fi

# ---------------------------------------------------------------------------
# Admin token — generate once, never overwrite
# ---------------------------------------------------------------------------
if [[ -z "$EXISTING_TOKEN" ]]; then
  ADMIN_TOKEN=$(openssl rand -hex 32)
  TOKEN_STATUS="Admin token newly generated."
  TOKEN_IS_NEW=true
else
  ADMIN_TOKEN="$EXISTING_TOKEN"
  TOKEN_STATUS="Admin token preserved from existing installation."
  TOKEN_IS_NEW=false
fi

# ---------------------------------------------------------------------------
# Java 21
# ---------------------------------------------------------------------------
JAVA_MAJOR=0
if command -v java >/dev/null 2>&1; then
  JAVA_MAJOR=$(java -version 2>&1 | head -1 | grep -oE '"[0-9]+' | tr -d '"')
fi

if [[ "$JAVA_MAJOR" -lt 21 ]]; then
  echo "Installing OpenJDK 21..."
  DEBIAN_FRONTEND=noninteractive apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y openjdk-21-jre-headless
  JAVA_STATUS="OpenJDK 21 (headless JRE) installed."
else
  JAVA_STATUS="Java ${JAVA_MAJOR} already present — meets the minimum of 21."
fi

# ---------------------------------------------------------------------------
# System user
# ---------------------------------------------------------------------------
id -u "$SERVICE_USER" >/dev/null 2>&1 \
  || useradd -r -m -s /usr/sbin/nologin "$SERVICE_USER"

# ---------------------------------------------------------------------------
# Download and install JAR
# ---------------------------------------------------------------------------
mkdir -p "$INSTALL_DIR"
echo "Downloading ${JAR_NAME} (${VERSION})..."
curl -fsSL "$JAR_URL" -o "$INSTALL_DIR/$JAR_NAME"

# Generic symlink so the systemd unit never needs to change across versions
ln -sf "$JAR_NAME" "$INSTALL_DIR/plp-fido2.jar"

# ---------------------------------------------------------------------------
# application.properties
# ---------------------------------------------------------------------------
cat > "$INSTALL_DIR/application.properties" << EOF
# HTTP port.
server.port=${PORT}

# IP addresses allowed to call this service (comma-separated).
# Keep this as localhost-only when running behind a reverse proxy.
allowed.ips=${ALLOWED_IPS}

# Jetty thread pool.
jetty.minThreads=2
jetty.maxThreads=${MAX_THREADS}

# Admin token — protects /admin/ routes.
# Generate a new one with: openssl rand -hex 32
admin.token=${ADMIN_TOKEN}

# External path prefix (nginx location strip).
# Must match the nginx location block (e.g. /webauthn).
app.path.prefix=${PREFIX}
EOF

chown -R "$SERVICE_USER:$SERVICE_USER" "$INSTALL_DIR"
chmod 600 "$INSTALL_DIR/application.properties"

# ---------------------------------------------------------------------------
# systemd service
# ---------------------------------------------------------------------------
cat > /etc/systemd/system/plp-fido2.service << EOF
[Unit]
Description=Phrase-Lock FIDO2 / WebAuthn Service
After=network.target

[Service]
User=${SERVICE_USER}
WorkingDirectory=${INSTALL_DIR}
ExecStart=/usr/bin/java -jar ${INSTALL_DIR}/plp-fido2.jar
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable plp-fido2 >/dev/null 2>&1 || true
systemctl restart plp-fido2

# Brief pause so the JVM has time to bind the port before we report success
sleep 2
if systemctl is-active --quiet plp-fido2; then
  SERVICE_STATUS="plp-fido2 is running."
else
  SERVICE_STATUS="WARNING: plp-fido2 did not start — check: journalctl -u plp-fido2"
fi

# ---------------------------------------------------------------------------
# nginx config hint (shown in summary, not applied automatically)
# ---------------------------------------------------------------------------
NGINX_HINT="location ${PREFIX}/ {
    proxy_pass          http://localhost:${PORT}/;
    proxy_set_header    Host              \$host;
    proxy_set_header    X-Real-IP         \$remote_addr;
    proxy_set_header    X-Forwarded-For   \$proxy_add_x_forwarded_for;
    proxy_set_header    X-Forwarded-Proto \$scheme;
}"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
TOKEN_LINE=""
if [[ "$TOKEN_IS_NEW" == true ]]; then
  TOKEN_LINE="
Admin token (save this!):
  ${ADMIN_TOKEN}"
fi

"$DIALOG" --title "plp-fido2 ${VERSION} Setup — Done" --msgbox \
"${JAVA_STATUS}

plp-fido2 ${VERSION} installed to:
  ${INSTALL_DIR}

${SERVICE_STATUS}
${TOKEN_STATUS}${TOKEN_LINE}

Admin UI (after nginx is configured):
  https://your.domain${PREFIX}/admin/users?token=<token>

Config file (token + settings):
  ${INSTALL_DIR}/application.properties

nginx block to add to your server{} section:
${NGINX_HINT}

To update later, just re-run this installer." 34 78
