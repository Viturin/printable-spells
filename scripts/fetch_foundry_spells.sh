#!/usr/bin/env bash
set -euo pipefail

VERSION="${1:-v13-dev}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DEST_DIR="$ROOT_DIR/src/generated/resources/foundry/spells"
VERSION_FILE="$ROOT_DIR/src/generated/resources/foundry/version.txt"
CACHE_DIR="$ROOT_DIR/.cache/foundry"
REPO_DIR="$CACHE_DIR/pf2e-$VERSION"
CACHE_TTL_DAYS="${CACHE_TTL_DAYS:-7}"

cleanup() {
  :
}
trap cleanup EXIT

is_cache_fresh() {
  [[ -d "$REPO_DIR/.git" ]] && find "$REPO_DIR" -maxdepth 0 -mtime "-$CACHE_TTL_DAYS" | grep -q .
}

echo "Using Foundry PF2e version: $VERSION"
mkdir -p "$CACHE_DIR"
if is_cache_fresh; then
  echo "Using cached Foundry repo at $REPO_DIR (fresh < $CACHE_TTL_DAYS days)."
else
  echo "Refreshing Foundry repo cache at $REPO_DIR"
  rm -rf "$REPO_DIR"
  git clone --depth 1 --branch "$VERSION" https://github.com/foundryvtt/pf2e.git "$REPO_DIR"
fi

rm -f "$DEST_DIR"/*.json "$DEST_DIR"/index.txt
mkdir -p "$DEST_DIR"

SOURCE_BASE="$REPO_DIR/packs/pf2e/spells"
SPELL_KINDS=("spells" "focus" "rituals")

count=0
for kind in "${SPELL_KINDS[@]}"; do
  kind_dir="$SOURCE_BASE/$kind"
  if [[ ! -d "$kind_dir" ]]; then
    echo "Missing expected directory: $kind_dir"
    continue
  fi

  while IFS= read -r -d '' file; do
    rel_path="${file#"$kind_dir"/}"
    safe_name="${kind}__$(echo "$rel_path" | tr '/' '__')"
    cp "$file" "$DEST_DIR/$safe_name"
    count=$((count + 1))
  done < <(find "$kind_dir" -type f -name '*.json' ! -name '_folders.json' -print0)
done

if [[ "$count" -eq 0 ]]; then
  echo "No spell JSON files found under $SOURCE_BASE/{spells,focus,rituals}."
  exit 1
fi

(
  cd "$DEST_DIR"
  ls -1 *.json | sort > index.txt
)

echo "$VERSION" > "$VERSION_FILE"

echo "Copied $count spell/focus/ritual JSON files to $DEST_DIR"
echo "Updated $VERSION_FILE"
