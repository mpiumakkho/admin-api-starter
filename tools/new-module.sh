#!/usr/bin/env bash
# Creates a business module under com.adminstarter.app with the layers contracts/structure.yaml requires.
# Usage: tools/new-module.sh <name>      (singular, lowercase: invoice, product)
#
# Then: declare its permissions in app/config/AppPermissions.java (and catalog.ts in admin-ng-starter), put
# @PreAuthorize on every endpoint, and add db/migration/app/V<n>__<name>.sql for its tables in schema app.
set -euo pipefail

name="${1:?usage: tools/new-module.sh <name>}"
if [[ ! "$name" =~ ^[a-z]+$ ]]; then
    echo "Module names are one lowercase word: $name" >&2
    exit 1
fi

root="$(cd "$(dirname "$0")/.." && pwd)/src/main/java/com/adminstarter/app/$name"
if [[ -e "$root" ]]; then
    echo "Already exists: $root" >&2
    exit 1
fi

for layer in controller service repository entity dto; do
    mkdir -p "$root/$layer"
    cat > "$root/$layer/package-info.java" <<EOF
@NullMarked
package com.adminstarter.app.$name.$layer;

import org.jspecify.annotations.NullMarked;
EOF
done
echo "Created app/$name/{controller,service,repository,entity,dto}"
