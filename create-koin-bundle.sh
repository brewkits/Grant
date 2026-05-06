#!/bin/bash

# Maven Central Bundle Creator — grant-core-koin only
# Use this script to publish grant-core-koin independently
# without touching already-published grant-core / grant-compose artifacts.

set -e

VERSION="1.3.1"
MODULE="grant-core-koin"
GROUP_PATH="dev/brewkits"
BUNDLE_NAME="${MODULE}-v${VERSION}-bundle.jar"
OUTPUT_DIR="maven-central-artifacts/v${VERSION}"
STAGING_DIR="${MODULE}/build/maven-central-staging/${GROUP_PATH}"

echo "=========================================="
echo "Maven Central Bundle Creator"
echo "${MODULE} v${VERSION}"
echo "=========================================="
echo ""

# ── Pre-flight checks ──────────────────────────────────────────────────────────

echo "🔍 Pre-flight checks..."

if ! command -v gpg &>/dev/null; then
    echo "❌ GPG not installed"
    exit 1
fi

if ! gpg --list-secret-keys 2>/dev/null | grep -q "brewkits\|vietnguyentuan"; then
    echo "❌ No GPG key found for brewkits/vietnguyentuan"
    exit 1
fi

echo "✅ GPG key found"
echo ""

# ── Step 1: Build & publish to local staging ──────────────────────────────────

echo "🔨 Building ${MODULE}..."
./gradlew clean :${MODULE}:build
./gradlew :${MODULE}:publishAllPublicationsToMavenCentralLocalRepository

if [ ! -d "${STAGING_DIR}" ]; then
    echo "❌ Staging directory not found: ${STAGING_DIR}"
    echo "   Check that publishing config exists in ${MODULE}/build.gradle.kts"
    exit 1
fi

echo "✅ Build complete"
echo ""

# ── Step 2: Checksums + GPG signatures ────────────────────────────────────────

echo "📊 Generating checksums and GPG signatures..."
echo ""

ROOT_DIR=$(pwd)
cd "${ROOT_DIR}/${STAGING_DIR}"

find . -type f \( \
    -name "*.jar"    -o \
    -name "*.aar"    -o \
    -name "*.pom"    -o \
    -name "*.module" -o \
    -name "*.klib"   -o \
    -name "*.zip"    -o \
    -name "*.json"   \
    \) ! -name "*.asc" ! -name "*.md5" ! -name "*.sha1" \
       ! -name "*.sha256" ! -name "*.sha512" | sort | while read file; do

    filename=$(basename "$file")
    echo "  Signing: $filename"

    # MD5
    (md5 -q "$file" 2>/dev/null || md5sum "$file" | awk '{print $1}') > "$file.md5"

    # SHA1
    shasum -a 1   "$file" | awk '{print $1}' > "$file.sha1"

    # SHA256
    shasum -a 256 "$file" | awk '{print $1}' > "$file.sha256"

    # SHA512
    shasum -a 512 "$file" | awk '{print $1}' > "$file.sha512"

    # GPG detached signature
    gpg --batch --yes --pinentry-mode loopback --armor --detach-sign "$file" 2>/dev/null

done

cd "${ROOT_DIR}"

echo ""
echo "✅ Checksums and signatures generated"
echo ""

# ── Step 3: Verify expected variants exist ────────────────────────────────────

echo "🔎 Verifying variants..."

EXPECTED_VARIANTS=(
    "${MODULE}"
    "${MODULE}-android"
    "${MODULE}-iosarm64"
    "${MODULE}-iossimulatorarm64"
    "${MODULE}-iosx64"
)

ALL_OK=true
for variant in "${EXPECTED_VARIANTS[@]}"; do
    VARIANT_DIR="${STAGING_DIR}/${variant}/${VERSION}"
    if [ -d "${VARIANT_DIR}" ]; then
        FILE_COUNT=$(find "${VARIANT_DIR}" -type f | wc -l | xargs)
        echo "  ✅ ${variant} (${FILE_COUNT} files)"
    else
        echo "  ❌ MISSING: ${variant}"
        ALL_OK=false
    fi
done

if [ "${ALL_OK}" = false ]; then
    echo ""
    echo "❌ One or more variants are missing. Aborting."
    exit 1
fi

echo ""

# ── Step 4: Package bundle ────────────────────────────────────────────────────

echo "📦 Creating bundle..."
echo ""

mkdir -p "${OUTPUT_DIR}"

TEMP_BUNDLE="${OUTPUT_DIR}/temp-koin-bundle"
rm -rf "${TEMP_BUNDLE}"
mkdir -p "${TEMP_BUNDLE}"

echo "  Copying artifacts..."
find "${STAGING_DIR}" -mindepth 1 -maxdepth 1 -type d | while read variant_dir; do
    variant_name=$(basename "${variant_dir}")
    mkdir -p "${TEMP_BUNDLE}/${GROUP_PATH}/${variant_name}/${VERSION}"
    cp -r "${variant_dir}/${VERSION}/"* \
          "${TEMP_BUNDLE}/${GROUP_PATH}/${variant_name}/${VERSION}/" 2>/dev/null || true
done

echo "  Creating ZIP..."
cd "${TEMP_BUNDLE}"
zip -q -r "../${BUNDLE_NAME}" "${GROUP_PATH}/"
cd "${ROOT_DIR}"

rm -rf "${TEMP_BUNDLE}"

BUNDLE_PATH="${OUTPUT_DIR}/${BUNDLE_NAME}"
BUNDLE_SIZE=$(du -h "${BUNDLE_PATH}" | awk '{print $1}')

echo "✅ Bundle created: ${BUNDLE_PATH} (${BUNDLE_SIZE})"
echo ""

# ── Step 5: Verify bundle ─────────────────────────────────────────────────────

echo "🔍 Verifying bundle..."
echo ""

if unzip -l "${BUNDLE_PATH}" 2>/dev/null | grep -q "META-INF"; then
    echo "  ❌ ERROR: Bundle contains META-INF — Maven Central will reject it"
    exit 1
fi
echo "  ✅ No META-INF"

TOTAL_FILES=$(unzip -l "${BUNDLE_PATH}" 2>/dev/null | tail -1 | awk '{print $2}')
SIG_COUNT=$(unzip -l "${BUNDLE_PATH}" 2>/dev/null | grep -c "\.asc$" || true)
POM_COUNT=$(unzip -l "${BUNDLE_PATH}" 2>/dev/null | grep -c "\.pom$" || true)
AAR_COUNT=$(unzip -l "${BUNDLE_PATH}" 2>/dev/null | grep -c "\.aar$" || true)
KLIB_COUNT=$(unzip -l "${BUNDLE_PATH}" 2>/dev/null | grep -c "\.klib$" || true)

echo "  📊 Total files  : ${TOTAL_FILES}"
echo "  🔐 GPG signatures: ${SIG_COUNT}"
echo "  📄 POM files    : ${POM_COUNT}"
echo "  🤖 AAR (Android): ${AAR_COUNT}"
echo "  🍎 KLIB (iOS)   : ${KLIB_COUNT}"

if [ "${SIG_COUNT}" -lt "${POM_COUNT}" ]; then
    echo ""
    echo "  ⚠️  WARNING: Fewer signatures than POM files — some artifacts may be unsigned"
fi

echo ""

# ── Summary ───────────────────────────────────────────────────────────────────

echo "=========================================="
echo "✅ ${MODULE} bundle ready!"
echo "=========================================="
echo ""
echo "Bundle : ${BUNDLE_PATH}"
echo "Size   : ${BUNDLE_SIZE}"
echo ""
echo "Next steps:"
echo "  1. Go to https://central.sonatype.com/"
echo "  2. Publishing → Upload via Publisher Portal"
echo "  3. Upload: ${BUNDLE_NAME}"
echo ""
echo "=========================================="
echo ""
