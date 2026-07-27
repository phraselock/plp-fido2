#!/bin/bash
# plp-fido2 uninstaller
#
# Usage:
#   curl -sSL https://raw.githubusercontent.com/phraselock/plp-fido2/main/uninstall.sh -o uninstall.sh
#   sudo bash uninstall.sh
#
set -euo pipefail

INSTALL_DIR="/opt/phraselock/fido2"
SERVICE_NAME="plp-fido2"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
SUMMARY_FILE="/opt/phraselock/fido2-setup.txt"

# ---------------------------------------------------------------------------
# Root check
# ---------------------------------------------------------------------------
if [[ "$(id -u)" -ne 0 ]]; then
  echo "Error: this uninstaller must run as root (sudo)." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Dialog tool
# ---------------------------------------------------------------------------
DIALOG=$(command -v whiptail 2>/dev/null || command -v dialog 2>/dev/null || true)
if [[ -z "$DIALOG" ]]; then
  echo "Error: whiptail or dialog is required." >&2
  exit 1
fi

# ---------------------------------------------------------------------------
# Confirmation
# ---------------------------------------------------------------------------
if ! "$DIALOG" --title "plp-fido2 Uninstall" --yesno \
    "This will stop and remove the plp-fido2 service and all its files in:\n\n  ${INSTALL_DIR}\n\nThe SQLite database (phraselockWebAuthn.db) will also be deleted.\n\nContinue?" \
    14 65; then
  echo "Aborted." >&2
  exit 0
fi

# ---------------------------------------------------------------------------
# HTML pages — ask separately (they live outside INSTALL_DIR)
# ---------------------------------------------------------------------------
HTML_DIR=""
if [[ -f "$INSTALL_DIR/.html_dir" ]]; then
  HTML_DIR=$(cat "$INSTALL_DIR/.html_dir")
fi

REMOVE_HTML=false
if [[ -n "$HTML_DIR" && -d "$HTML_DIR" ]]; then
  if "$DIALOG" --title "plp-fido2 Uninstall" --yesno \
      "Also remove the HTML demo pages in:\n\n  ${HTML_DIR}" \
      10 65; then
    REMOVE_HTML=true
  fi
fi

# ---------------------------------------------------------------------------
# Stop and disable service
# ---------------------------------------------------------------------------
echo "Stopping plp-fido2..."
systemctl stop  "$SERVICE_NAME" 2>/dev/null || true
systemctl disable "$SERVICE_NAME" 2>/dev/null || true

# ---------------------------------------------------------------------------
# Remove service file
# ---------------------------------------------------------------------------
rm -f "$SERVICE_FILE"
systemctl daemon-reload

# ---------------------------------------------------------------------------
# Remove install directory
# ---------------------------------------------------------------------------
rm -rf "$INSTALL_DIR"
rm -f  "$SUMMARY_FILE"

# ---------------------------------------------------------------------------
# Remove HTML pages (optional)
# ---------------------------------------------------------------------------
if [[ "$REMOVE_HTML" == true ]]; then
  rm -rf "$HTML_DIR"
  echo "HTML pages removed from ${HTML_DIR}."
fi

# ---------------------------------------------------------------------------
# Done
# ---------------------------------------------------------------------------
"$DIALOG" --title "plp-fido2 Uninstall — Done" --msgbox \
"plp-fido2 has been removed.

Removed:
  - systemd service (${SERVICE_NAME})
  - ${INSTALL_DIR}
  - ${SUMMARY_FILE}
$(if [[ "$REMOVE_HTML" == true ]]; then echo "  - ${HTML_DIR}"; fi)

The 'phraselock' system user was kept in case other
PhraseLock services are still running on this host.
Remove it manually if no longer needed:
  userdel phraselock" 20 65
