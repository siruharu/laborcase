#!/usr/bin/env bash
# Verify that ai/prompts is a submodule pointer (not tracked as regular files)
# and that ai/prompts.example/ contains at least one markdown sample.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

failed=0

if [[ ! -f .gitmodules ]]; then
  echo "✗ .gitmodules missing" >&2
  failed=1
elif ! grep -q 'path = ai/prompts' .gitmodules; then
  echo "✗ .gitmodules does not declare ai/prompts" >&2
  failed=1
fi

tracked_under_prompts="$(git ls-files -- 'ai/prompts/*' 2>/dev/null | grep -v '^ai/prompts$' || true)"
if [[ -n "$tracked_under_prompts" ]]; then
  echo "✗ ai/prompts contains regular tracked files (should only be submodule pointer):" >&2
  echo "$tracked_under_prompts" | sed 's/^/    /' >&2
  failed=1
fi

sample_count="$(find ai/prompts.example -name '*.md' -type f 2>/dev/null | wc -l | tr -d ' ')"
if [[ "$sample_count" -lt 1 ]]; then
  echo "✗ ai/prompts.example/ has no .md samples" >&2
  failed=1
fi

if [[ $failed -eq 0 ]]; then
  echo "✓ submodule check passed (ai/prompts.example has $sample_count sample(s))"
fi

exit $failed
