#!/usr/bin/env bash
# Makes an RS256 key pair for access tokens and the key for secrets kept in the database, and prints the environment
# variables application-prod.yml reads.
# Usage: tools/gen-keys.sh [kid]     (kid defaults to k-<date>)
#
# To rotate: add the new pair as the last entry of access-control.token.keys and keep the old one until every
# token it signed has run out (15 minutes); tokens are signed with the last key and checked against all of them.
set -euo pipefail

kid="${1:-k-$(date +%Y%m%d)}"
dir="$(mktemp -d)"
trap 'rm -rf "$dir"' EXIT

openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out "$dir/private.pem" 2>/dev/null
openssl pkey -in "$dir/private.pem" -pubout -out "$dir/public.pem"

# One line each, as environment variables hold them; JwtKeyProvider ignores the PEM header lines
one_line() { grep -v -- '-----' "$1" | tr -d '\n'; }

echo "AC_JWT_KID=$kid"
echo "AC_JWT_PRIVATE_KEY=$(one_line "$dir/private.pem")"
echo "AC_JWT_PUBLIC_KEY=$(one_line "$dir/public.pem")"
# Generate these once and keep them: changing either makes the stored two-step sign-in secrets unreadable
echo "AC_ENCRYPTION_PASSWORD=$(openssl rand -hex 32)"
echo "AC_ENCRYPTION_SALT=$(openssl rand -hex 16)"
