#!/usr/bin/env bash
# Regression test: ensure pre-commit blocks a fake GCP service-account JSON.
# Creates a temp file with a plausible-looking private key, stages it,
# runs pre-commit, and expects a non-zero exit.
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

SCRATCH="$(mktemp -d)"
trap 'rm -rf "$SCRATCH"; git restore --staged leak-sample.json 2>/dev/null || true; rm -f leak-sample.json' EXIT

cat > leak-sample.json <<'JSON'
{
  "type": "service_account",
  "project_id": "fake-project",
  "private_key_id": "0123456789abcdef0123456789abcdef01234567",
  "private_key": "-----BEGIN PRIVATE KEY-----\nMIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQC7VJTUt9Us8cKj\nMzEfYyjiWA4R4/M2bS1GB4t7NXp98C3SC6dVMvDuictGeurT8jNbvJZHtCSuYEvu\nNMoSfm76oqFvAp8Gy0iz5sxjZmSnXyCdPEovGhLa0VzMaQ8s+CLOyS56YyCFGeJZ\nqgtzJ6GR3eqoYSW9b9UMvkBpZODSctWSNGj3P7jRFDO5VoTwCQAWbFnOjDfH5Ulg\np2PKSQnSJP3AJLQNFNe7br1XbrhV//eO+t51mIpGSDCUv3E0DDFcWDTH9cXDTTlR\nZVEiR2BwpZOOkE/Z0/BVnhZYL71oZV34bKfWjQIt6V/isSMahdsAASACp4ZTGtwi\nVuNd9tybAgMBAAECggEBAKTmjaS6tkK8BlPXClTQ2vpz/N6uxDeS35mXpqasqskV\n-----END PRIVATE KEY-----\n",
  "client_email": "fake@fake-project.iam.gserviceaccount.com"
}
JSON

git add leak-sample.json
set +e
pre-commit run --files leak-sample.json
rc=$?
set -e

if [[ $rc -eq 0 ]]; then
  echo "✗ pre-commit did NOT block the fake secret (exit=0)"
  exit 1
fi

echo "✓ pre-commit blocked the fake secret (exit=$rc)"
