#!/bin/bash
set -euo pipefail

# Builds the plp-fido2 release artifacts:
#   - JAR via Gradle shadowJar
#   - HTML demo pages as a clean tar.gz (no macOS metadata)
#
# Usage: ./build-release.sh
#   Version is read from build.gradle (archiveVersion.set(...)).

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

# ---------------------------------------------------------------------------
# Read version from build.gradle
# ---------------------------------------------------------------------------
VERSION=$(grep "archiveVersion\.set(" build.gradle \
  | sed -E "s/.*archiveVersion\.set\('([^']+)'\).*/\1/")

if [[ -z "$VERSION" ]]; then
  echo "Error: could not read version from build.gradle" >&2
  exit 1
fi

echo "Building plp-fido2 ${VERSION} ..."

# ---------------------------------------------------------------------------
# Build JAR
# ---------------------------------------------------------------------------
./gradlew shadowJar

JAR_FILE="build/libs/plp-fido2-${VERSION}.jar"
if [[ ! -f "$JAR_FILE" ]]; then
  echo "Error: expected JAR not found at ${JAR_FILE}" >&2
  exit 1
fi
echo "  JAR  -> $(pwd)/${JAR_FILE}"

# ---------------------------------------------------------------------------
# Build HTML tar.gz (macOS-safe: no extended attributes or .DS_Store)
# ---------------------------------------------------------------------------
if [[ ! -d "html" ]]; then
  echo "Error: html/ directory not found" >&2
  exit 1
fi

# Strip macOS extended attributes (quarantine, Spotlight, Finder tags) so
# they don't end up as PAX headers in the archive (COPYFILE_DISABLE alone
# is not enough — libarchive still stores xattrs per-entry).
if command -v xattr >/dev/null 2>&1; then
  xattr -cr html/ 2>/dev/null || true
fi

HTML_TARBALL="plp-fido2-html-${VERSION}.tar.gz"
COPYFILE_DISABLE=1 tar -czf "$HTML_TARBALL" \
  --exclude='.DS_Store' \
  --exclude='._*' \
  --exclude='.AppleDouble' \
  -C html .
echo "  HTML -> $(pwd)/${HTML_TARBALL}"

echo "Done."
