#!/usr/bin/env bash
# Regression test for scripts/sync-docs.sh
# Creates a fake Obsidian vault with 3 notes (done / draft / with-private-block)
# and verifies the script includes only the done note with the private block stripped.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

SCRATCH="$(mktemp -d)"
trap 'rm -rf "$SCRATCH"' EXIT

VAULT="$SCRATCH/vault"
mkdir -p "$VAULT/10_Projects/fakeproj/01_Research"

# Note A: status done, no private block
cat > "$VAULT/10_Projects/fakeproj/01_Research/a-done.md" <<'MD'
---
status: done
---
# Note A
Public content only.
Wiki link: [[b-draft]].
MD

# Note B: status draft (should be excluded)
cat > "$VAULT/10_Projects/fakeproj/01_Research/b-draft.md" <<'MD'
---
status: draft
---
# Note B
Should not be copied.
MD

# Note C: status done with private block
cat > "$VAULT/10_Projects/fakeproj/01_Research/c-private.md" <<'MD'
---
status: done
---
# Note C
Public content before.
<private>
SECRET: should be stripped
</private>
Public content after.
MD

DEST="$SCRATCH/tmp-repo-copy"
mkdir -p "$DEST/docs/research"

# Run in APPLY mode against our scratch source
VAULT_PATH="$VAULT" PROJECT="fakeproj" bash -c "
  cd '$(pwd)'
  # Redirect the script's REPO_ROOT by running in the scratch repo dir
  mkdir -p '$DEST/docs'
  cp scripts/sync-docs.sh '$DEST/sync-docs.sh'
  cd '$DEST' && git init -q && git add -A && git -c user.name=t -c user.email=t@t commit -q -m init
  VAULT_PATH='$VAULT' PROJECT='fakeproj' ./sync-docs.sh --apply
" > "$SCRATCH/run.log" 2>&1 || true

cat "$SCRATCH/run.log"

failed=0

if [[ ! -f "$DEST/docs/research/a-done.md" ]]; then
  echo "✗ a-done.md should have been copied"
  failed=1
fi

if [[ -f "$DEST/docs/research/b-draft.md" ]]; then
  echo "✗ b-draft.md should NOT have been copied (status=draft)"
  failed=1
fi

if [[ ! -f "$DEST/docs/research/c-private.md" ]]; then
  echo "✗ c-private.md should have been copied"
  failed=1
fi

if grep -q 'SECRET: should be stripped' "$DEST/docs/research/c-private.md" 2>/dev/null; then
  echo "✗ private block was NOT stripped from c-private.md"
  failed=1
fi

if ! grep -q '\[b-draft\](\./b-draft\.md)' "$DEST/docs/research/a-done.md" 2>/dev/null; then
  echo "✗ wiki link was not rewritten in a-done.md"
  grep -n 'b-draft' "$DEST/docs/research/a-done.md" || true
  failed=1
fi

if [[ $failed -eq 0 ]]; then
  echo "✓ sync-docs.sh regression test passed"
fi

exit $failed
