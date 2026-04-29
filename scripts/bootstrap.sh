#!/usr/bin/env bash
# One-shot bootstrap for new developers on laborcase.
# Idempotent: safe to run multiple times.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

say() { printf '\033[1;36m▶\033[0m %s\n' "$*"; }
ok()  { printf '\033[1;32m✓\033[0m %s\n' "$*"; }
warn(){ printf '\033[1;33m⚠\033[0m %s\n' "$*"; }
die() { printf '\033[1;31m✗ %s\033[0m\n' "$*" >&2; exit 1; }

# ─────────────────────────────────────────────────────────────
# 1. Required CLI tools
# ─────────────────────────────────────────────────────────────
say "Checking required tools"
REQUIRED=(git gh python3 pre-commit gitleaks detect-secrets)
missing=()
for cmd in "${REQUIRED[@]}"; do
  if ! command -v "$cmd" >/dev/null 2>&1; then
    missing+=("$cmd")
  fi
done
if (( ${#missing[@]} > 0 )); then
  warn "Missing: ${missing[*]}"
  if [[ "$(uname)" == "Darwin" ]]; then
    echo "  Install with: brew install ${missing[*]}"
  else
    echo "  Please install the missing tools and re-run."
  fi
  die "Cannot continue without required tools."
fi
ok "All required tools present"

# ─────────────────────────────────────────────────────────────
# 2. Submodule init (graceful fallback)
# ─────────────────────────────────────────────────────────────
say "Initializing submodules"
if git submodule update --init --recursive 2>/dev/null; then
  prompt_count="$(find ai/prompts -maxdepth 2 -type f 2>/dev/null | wc -l | tr -d ' ')"
  if [[ "$prompt_count" -gt 1 ]]; then
    ok "ai/prompts submodule checked out ($prompt_count files)"
  else
    warn "ai/prompts submodule is empty. Using ai/prompts.example/ as fallback."
  fi
else
  warn "Could not fetch ai/prompts submodule (no access?). Using ai/prompts.example/ as fallback."
  echo "     If you need the real prompts, ask a maintainer for read access to"
  echo "     https://github.com/siruharu/laborcase-internal"
fi

# ─────────────────────────────────────────────────────────────
# 3. pre-commit hook install
# ─────────────────────────────────────────────────────────────
say "Installing pre-commit hooks"
pre-commit install --install-hooks >/dev/null
ok "pre-commit hooks installed at .git/hooks/pre-commit"

# ─────────────────────────────────────────────────────────────
# 4. Hygiene verification
# ─────────────────────────────────────────────────────────────
say "Verifying repository hygiene"
./scripts/verify-initial-hygiene.sh
./scripts/verify-submodule.sh

# ─────────────────────────────────────────────────────────────
# 5. Env file template
# ─────────────────────────────────────────────────────────────
if [[ -f .env.example && ! -f .env ]]; then
  cp .env.example .env
  ok "Copied .env.example → .env (fill in values before running services)"
fi

# ─────────────────────────────────────────────────────────────
# 6. Next steps
# ─────────────────────────────────────────────────────────────
cat <<EOF

\033[1;32mBootstrap complete.\033[0m

Next steps:
  • Read docs/decisions/adr-0001-repo-split.md for the repo architecture
  • Run service-specific setup from frontend/, api/, ai/ when those land
  • Day-to-day work happens on the 'dev' branch:
        git checkout dev && git pull
        git checkout -b feature/<short-name>

EOF
