#!/usr/bin/env bash
set -euo pipefail

VERSION_INPUT="${1:-latest}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST_DIR="$ROOT_DIR/spellcards-app/src/generated/resources/foundry/spells"
VERSION_FILE="$ROOT_DIR/spellcards-app/src/generated/resources/foundry/version.txt"
DEST_FILE="$DEST_DIR/spells.json"
TMP_DIR=""

cleanup() {
  if [[ -n "$TMP_DIR" && -d "$TMP_DIR" ]]; then
    rm -rf "$TMP_DIR"
  fi
}
trap cleanup EXIT

require_cmd() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1"
    exit 1
  fi
}

resolve_version() {
  if [[ "$VERSION_INPUT" != "latest" ]]; then
    echo "$VERSION_INPUT"
    return
  fi

  local latest_json
  latest_json="$(curl -fsSL https://api.github.com/repos/foundryvtt/pf2e/releases/latest)"
  local latest_tag
  latest_tag="$(printf '%s\n' "$latest_json" | sed -n 's/^[[:space:]]*"tag_name":[[:space:]]*"\([^"]*\)".*/\1/p' | head -n 1)"
  if [[ -z "$latest_tag" ]]; then
    echo "Unable to resolve latest release tag from GitHub API response."
    exit 1
  fi
  echo "$latest_tag"
}

extract_spells_json() {
  local zip_file="$1"
  local output_file="$2"

  if command -v unzip >/dev/null 2>&1; then
    unzip -p "$zip_file" packs/spells.json > "$output_file"
    return
  fi

  if command -v jar >/dev/null 2>&1; then
    local tmp_dir
    tmp_dir="$(mktemp -d)"
    (
      cd "$tmp_dir"
      jar xf "$zip_file" packs/spells.json
      cp packs/spells.json "$output_file"
    )
    rm -rf "$tmp_dir"
    return
  fi

  echo "Missing required archive extractor. Install 'unzip' or make 'jar' available."
  exit 1
}

require_cmd curl
VERSION="$(resolve_version)"
ASSET_URL="https://github.com/foundryvtt/pf2e/releases/download/$VERSION/json-assets.zip"

echo "Using Foundry PF2e release: $VERSION"

if [[ -s "$DEST_FILE" && -f "$VERSION_FILE" ]]; then
  current_version="$(tr -d '[:space:]' < "$VERSION_FILE" || true)"
  if [[ "$current_version" == "$VERSION" ]]; then
    echo "Found generated spells for version $VERSION at $DEST_FILE. Skipping download."
    exit 0
  fi
fi

TMP_DIR="$(mktemp -d)"
ZIP_FILE="$TMP_DIR/json-assets.zip"
SPELLS_JSON="$TMP_DIR/spells.json"

echo "Downloading $ASSET_URL"
curl -fL "$ASSET_URL" -o "$ZIP_FILE"

extract_spells_json "$ZIP_FILE" "$SPELLS_JSON"

rm -f "$DEST_DIR"/*.json "$DEST_DIR"/index.txt 2>/dev/null || true
mkdir -p "$DEST_DIR"

cp "$SPELLS_JSON" "$DEST_FILE"

if [[ ! -s "$DEST_FILE" ]]; then
  echo "Generated spells file is empty: $DEST_FILE"
  exit 1
fi

echo "$VERSION" > "$VERSION_FILE"

echo "Copied packs/spells.json to $DEST_FILE"
echo "Updated $VERSION_FILE"
