#!/bin/bash
# plp-fido2 installer
# Downloads the latest release from GitHub and installs it as a systemd service.
#
# Usage:
#   curl -sSL https://raw.githubusercontent.com/phraselock/plp-fido2/main/install.sh -o install.sh
#   sudo bash install.sh
#
set -euo pipefail

GITHUB_REPO="phraselock/plp-fido2"
INSTALL_DIR="/opt/phraselock/fido2"
SERVICE_USER="phraselock"
SUMMARY_FILE="/opt/phraselock/fido2/fido2-setup.txt"

# ---------------------------------------------------------------------------
# Root check
# ---------------------------------------------------------------------------
if [[ "$(id -u)" -ne 0 ]]; then
  echo "Error: this installer must run as root (sudo)." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Dialog tool
# ---------------------------------------------------------------------------
DIALOG=$(command -v whiptail 2>/dev/null || command -v dialog 2>/dev/null || true)
if [[ -z "$DIALOG" ]]; then
  echo "Installing whiptail..." >&2
  DEBIAN_FRONTEND=noninteractive apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq whiptail
  DIALOG=$(command -v whiptail)
fi

# ---------------------------------------------------------------------------
# curl
# ---------------------------------------------------------------------------
if ! command -v curl >/dev/null 2>&1; then
  DEBIAN_FRONTEND=noninteractive apt-get update -qq
  DEBIAN_FRONTEND=noninteractive apt-get install -y -qq curl
fi

# ---------------------------------------------------------------------------
# Fetch latest release from GitHub
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
HTML_URL=$(echo "$RELEASE_JSON" \
  | grep '"browser_download_url"' \
  | grep 'html.*\.tar\.gz"' \
  | sed -E 's/.*"browser_download_url"[[:space:]]*:[[:space:]]*"([^"]+)".*/\1/')

if [[ -z "$VERSION" || -z "$JAR_URL" ]]; then
  echo "Error: could not parse GitHub release info. Check your internet connection." >&2
  exit 1
fi

JAR_NAME=$(basename "$JAR_URL")

# ---------------------------------------------------------------------------
# Read existing config values (upgrade-friendly)
# ---------------------------------------------------------------------------
_get() { grep "^${1}=" "$INSTALL_DIR/application.properties" 2>/dev/null | cut -d= -f2- || true; }

EXISTING_PORT="8081"
EXISTING_PREFIX="/webauthn"
EXISTING_IPS="127.0.0.1,::1,[0:0:0:0:0:0:0:1]"
EXISTING_MAX_THREADS="10"
EXISTING_TOKEN=""
EXISTING_HTML_DIR="/var/www/html/fido-test"

if [[ -f "$INSTALL_DIR/application.properties" ]]; then
  EXISTING_PORT=$(   _get server.port      || echo "$EXISTING_PORT")
  EXISTING_PREFIX=$( _get app.path.prefix  || echo "$EXISTING_PREFIX")
  EXISTING_IPS=$(    _get allowed.ips      || echo "$EXISTING_IPS")
  EXISTING_MAX_THREADS=$(_get jetty.maxThreads || echo "$EXISTING_MAX_THREADS")
  EXISTING_TOKEN=$(  _get admin.token      || echo "")
fi

# Preserve previously configured HTML dir
if [[ -f "$INSTALL_DIR/.html_dir" ]]; then
  EXISTING_HTML_DIR=$(cat "$INSTALL_DIR/.html_dir")
fi

TITLE="plp-fido2 ${VERSION} Setup"

# ---------------------------------------------------------------------------
# Interactive configuration
# ---------------------------------------------------------------------------
if ! PORT=$("$DIALOG" --title "$TITLE" \
    --inputbox "HTTP port for plp-fido2:" 10 55 "$EXISTING_PORT" \
    3>&1 1>&2 2>&3); then echo "Aborted." >&2; exit 1; fi

if ! PREFIX=$("$DIALOG" --title "$TITLE" \
    --inputbox "nginx location prefix (e.g. /webauthn):" 10 55 "$EXISTING_PREFIX" \
    3>&1 1>&2 2>&3); then echo "Aborted." >&2; exit 1; fi

if ! ALLOWED_IPS=$("$DIALOG" --title "$TITLE" \
    --inputbox "Allowed IPs (comma-separated):" 10 70 "$EXISTING_IPS" \
    3>&1 1>&2 2>&3); then echo "Aborted." >&2; exit 1; fi

if ! MAX_THREADS=$("$DIALOG" --title "$TITLE" \
    --inputbox "Jetty max threads (concurrent requests):" 10 55 "$EXISTING_MAX_THREADS" \
    3>&1 1>&2 2>&3); then echo "Aborted." >&2; exit 1; fi

if ! HTML_DIR=$("$DIALOG" --title "$TITLE" \
    --inputbox "Web root directory for HTML demo pages:" 10 65 "$EXISTING_HTML_DIR" \
    3>&1 1>&2 2>&3); then echo "Aborted." >&2; exit 1; fi

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

# Remember HTML dir for future upgrades
echo "$HTML_DIR" > "$INSTALL_DIR/.html_dir"

chown -R "$SERVICE_USER:$SERVICE_USER" "$INSTALL_DIR"
chmod 600 "$INSTALL_DIR/application.properties"

# ---------------------------------------------------------------------------
# Download HTML demo pages from release and patch API_BASE
# ---------------------------------------------------------------------------
mkdir -p "$HTML_DIR"
if [[ -n "$HTML_URL" ]]; then
  echo "Downloading HTML demo pages (${VERSION})..."
  TMPTAR=$(mktemp /tmp/plp-fido2-html.XXXXXX.tar.gz)
  curl -fsSL "$HTML_URL" -o "$TMPTAR"
  tar -xzf "$TMPTAR" -C "$HTML_DIR"
  rm -f "$TMPTAR"
  # Patch API_BASE in all HTML files to match the configured prefix
  find "$HTML_DIR" -name "*.html" -exec \
    sed -i "s|const API_BASE = '[^']*'|const API_BASE = '${PREFIX}'|g" {} \;
  HTML_STATUS="HTML pages installed to ${HTML_DIR} (API_BASE set to '${PREFIX}')."
else
  HTML_STATUS="WARNING: no HTML archive found in release ${VERSION} — pages not installed."
fi

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

sleep 2
if systemctl is-active --quiet plp-fido2; then
  SERVICE_STATUS="plp-fido2 is running."
else
  SERVICE_STATUS="WARNING: plp-fido2 did not start — check: journalctl -u plp-fido2"
fi

# ---------------------------------------------------------------------------
# nginx blocks
# ---------------------------------------------------------------------------
NGINX_SERVICE="location ${PREFIX}/ {
    proxy_pass          http://localhost:${PORT}/;
    proxy_set_header    Host              \$host;
    proxy_set_header    X-Real-IP         \$remote_addr;
    proxy_set_header    X-Forwarded-For   \$proxy_add_x_forwarded_for;
    proxy_set_header    X-Forwarded-Proto \$scheme;
}"

NGINX_HTML="location /fido-test/ {
    # serves /var/www/html/fido-test/ (root must be set above)
    try_files \$uri \$uri/ =404;
}"

# ---------------------------------------------------------------------------
# Summary text file
# ---------------------------------------------------------------------------
mkdir -p "$(dirname "$SUMMARY_FILE")"

TOKEN_DISPLAY="(preserved — see ${INSTALL_DIR}/application.properties)"
if [[ "$TOKEN_IS_NEW" == true ]]; then
  TOKEN_DISPLAY="${ADMIN_TOKEN}"
fi

cat > "$SUMMARY_FILE" << EOF
plp-fido2 ${VERSION} — Installation Summary
$(date)
============================================================

${JAVA_STATUS}
${SERVICE_STATUS}
${TOKEN_STATUS}

Admin token:
  ${TOKEN_DISPLAY}

Admin UI:
  https://your.domain${PREFIX}/admin/users?token=${ADMIN_TOKEN}

Service config:
  ${INSTALL_DIR}/application.properties

${HTML_STATUS}

Demo pages (after nginx is configured):
  https://your.domain/fido-test/register.html
  https://your.domain/fido-test/login.html
  https://your.domain/fido-test/dashboard.html

============================================================
nginx — add both blocks to your server{} section:

# plp-fido2 backend
${NGINX_SERVICE}

# FIDO2 demo pages
${NGINX_HTML}
============================================================

To update: sudo bash install.sh
To remove:  sudo bash uninstall.sh
EOF

chmod 600 "$SUMMARY_FILE"

# ---------------------------------------------------------------------------
# Summary dialog
# ---------------------------------------------------------------------------
TOKEN_LINE=""
if [[ "$TOKEN_IS_NEW" == true ]]; then
  TOKEN_LINE="
Admin token (save this!):
  ${ADMIN_TOKEN}
"
fi

"$DIALOG" --title "plp-fido2 ${VERSION} Setup — Done" --msgbox \
"${JAVA_STATUS}
${SERVICE_STATUS}
${TOKEN_STATUS}
${TOKEN_LINE}
Demo pages: ${HTML_DIR}
  (API_BASE set to '${PREFIX}')

nginx blocks — add to your server{} section:

${NGINX_SERVICE}

${NGINX_HTML}

Full summary saved to:
  ${SUMMARY_FILE}

To update: sudo bash install.sh
To remove:  sudo bash uninstall.sh" 40 78
