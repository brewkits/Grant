#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."

if [ "$(uname -s)" != Darwin ] || [ "$(uname -m)" != arm64 ]; then
    echo "Desktop artifact verification requires an Apple Silicon Mac." >&2
    exit 1
fi
preview_version=$(sed -n 's/^version = "\(.*\)"$/\1/p' grant-desktop/build.gradle.kts)
test -n "$preview_version"

bridge_args=()
bridge_dir="${1:-${GRANT_WINDOWS_BRIDGE_DIR:-}}"
if [ -n "$bridge_dir" ]; then
    bridge_args+=("-PgrantWindowsBridgeDir=$bridge_dir")
fi
./gradlew "${bridge_args[@]}" :grant-desktop:jvmTest :grant-desktop:checkKotlinAbi \
    :grant-core:publishKotlinMultiplatformPublicationToMavenCentralLocalRepository \
    :grant-core:publishJvmPublicationToMavenCentralLocalRepository \
    :grant-desktop:publishAllPublicationsToMavenCentralLocalRepository \
    :grant-bom:publishAllPublicationsToMavenCentralLocalRepository

./gradlew -p verification/desktop-consumer "-PgrantVersion=$preview_version" \
    createDistributable run

echo "Artifact/ABI verification passed. Packaged-app consent and signing gates remain manual."
