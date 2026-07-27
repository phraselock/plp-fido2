#!/bin/bash
set -euo pipefail

# Builds the plp-fido2 release artifacts via build-release.sh, then
# publishes them as a new GitHub release — tags the current commit,
# pushes the tag, uploads JAR + HTML tar.gz, and removes the local files.
#
# Usage: ./upload-release.sh ["release notes"]
#   Version is read from build.gradle (archiveVersion.set(...)).
#   ["release notes"]  optional, defaults to a generic note.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

REPO="phraselock/plp-fido2"

NOTES="${1:-}"

command -v gh >/dev/null 2>&1 || { echo "Error: gh CLI not found." >&2; exit 1; }
gh auth status >/dev/null 2>&1 || { echo "Error: gh is not authenticated (run 'gh auth login')." >&2; exit 1; }

# ---------------------------------------------------------------------------
# Read version from build.gradle
# ---------------------------------------------------------------------------
VERSION=$(grep "archiveVersion\.set(" build.gradle \
  | sed -E "s/.*archiveVersion\.set\('([^']+)'\).*/\1/")

if [[ -z "$VERSION" ]]; then
  echo "Error: could not read version from build.gradle" >&2
  exit 1
fi

TAG="v${VERSION}"
NOTES="${NOTES:-Release ${TAG}.}"

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "Error: tag ${TAG} already exists — pick a different version or delete the tag first." >&2
  exit 1
fi

if [[ -n "$(git status --porcelain)" ]]; then
  echo "Warning: working tree has uncommitted changes — the release is built from what's" >&2
  echo "on disk now, but the tag will point at the current commit, not these changes." >&2
fi

# ---------------------------------------------------------------------------
# Build
# ---------------------------------------------------------------------------
./build-release.sh

JAR_FILE="build/libs/plp-fido2-${VERSION}.jar"
HTML_TARBALL="plp-fido2-html-${VERSION}.tar.gz"

# ---------------------------------------------------------------------------
# Tag, push, create release
# ---------------------------------------------------------------------------
git tag "$TAG"
git push origin "$TAG"

gh release create "$TAG" "$JAR_FILE" "$HTML_TARBALL" \
  --repo "$REPO" \
  --title "$TAG" \
  --notes "$NOTES"

rm -f "$HTML_TARBALL"

echo "Done: https://github.com/${REPO}/releases/tag/${TAG}"
