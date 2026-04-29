#!/usr/bin/env bash
# Selectively copy finalized Obsidian Vault notes into docs/{research,analysis,plans}/.
#
# Filters:
#   1) frontmatter `status` must be one of: done, approved
#   2) <private>...</private> blocks are stripped
#   3) Obsidian wiki links [[foo]] are rewritten to ./foo.md
#
# Usage:
#   VAULT_PATH=/Users/zephyr/ObsidianVault PROJECT=laborcase ./scripts/sync-docs.sh        # dry-run
#   VAULT_PATH=... PROJECT=... ./scripts/sync-docs.sh --apply                               # actually write
set -euo pipefail

VAULT_PATH="${VAULT_PATH:-/Users/zephyr/ObsidianVault}"
PROJECT="${PROJECT:-laborcase}"
APPLY=false
if [[ "${1:-}" == "--apply" ]]; then APPLY=true; fi

REPO_ROOT="$(git rev-parse --show-toplevel)"
SRC_BASE="${VAULT_PATH}/10_Projects/${PROJECT}"

declare -A DIR_MAP=(
  ["01_Research"]="docs/research"
  ["02_Analysis"]="docs/analysis"
  ["03_Plan"]="docs/plans"
)

ALLOWED_STATUS_REGEX='^status:[[:space:]]*(done|approved)[[:space:]]*$'

tmpdir="$(mktemp -d)"
trap 'rm -rf "$tmpdir"' EXIT

declare -i considered=0 included=0 excluded=0

process_file() {
  local src="$1" dest="$2"
  considered=$((considered + 1))

  # Extract frontmatter (between first two --- lines)
  local frontmatter
  frontmatter="$(awk '/^---$/{c++; if (c==2) exit; next} c==1{print}' "$src")"

  if ! echo "$frontmatter" | grep -Eq "$ALLOWED_STATUS_REGEX"; then
    excluded=$((excluded + 1))
    echo "  ✗ skip (status not done/approved): $src"
    return
  fi

  # Strip <private>...</private> blocks and rewrite wiki links
  local processed="$tmpdir/$(basename "$src")"
  awk '
    BEGIN { in_priv = 0 }
    /<private>/ { in_priv = 1; next }
    /<\/private>/ { in_priv = 0; next }
    !in_priv { print }
  ' "$src" \
  | sed -E 's/\[\[([^\|]+)\|([^]]+)\]\]/[\2](.\/\1.md)/g; s/\[\[([^]]+)\]\]/[\1](.\/\1.md)/g' \
  > "$processed"

  included=$((included + 1))

  if $APPLY; then
    mkdir -p "$(dirname "$dest")"
    if ! cmp -s "$processed" "$dest" 2>/dev/null; then
      cp "$processed" "$dest"
      echo "  ✓ wrote $dest"
    else
      echo "  = unchanged $dest"
    fi
  else
    if ! cmp -s "$processed" "$dest" 2>/dev/null; then
      echo "  + would write $dest"
    fi
  fi
}

echo "▶ Source: $SRC_BASE"
echo "▶ Target: $REPO_ROOT/docs/{research,analysis,plans}"
echo "▶ Mode:   $([[ $APPLY == true ]] && echo APPLY || echo DRY-RUN)"
echo ""

for src_dir in "${!DIR_MAP[@]}"; do
  dest_rel="${DIR_MAP[$src_dir]}"
  src_abs="${SRC_BASE}/${src_dir}"
  dest_abs="${REPO_ROOT}/${dest_rel}"

  if [[ ! -d "$src_abs" ]]; then
    echo "⚠ $src_abs not found, skipping"
    continue
  fi

  echo "▶ ${src_dir} → ${dest_rel}"
  while IFS= read -r -d '' f; do
    dest_file="${dest_abs}/$(basename "$f")"
    process_file "$f" "$dest_file"
  done < <(find "$src_abs" -maxdepth 1 -name '*.md' -type f -print0)
  echo ""
done

echo "Summary: considered=$considered, included=$included, excluded=$excluded"
if ! $APPLY && [[ $included -gt 0 ]]; then
  echo "Re-run with --apply to write changes."
fi
