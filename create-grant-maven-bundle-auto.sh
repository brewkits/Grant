#!/bin/bash

# Maven Central Bundle Creator for Grant Library
# Version: 1.1.0
# Auto-generates checksums and GPG signatures

set -e

VERSION="1.2.1"
GROUP_PATH="dev/brewkits"
BUNDLE_NAME="grant-v${VERSION}-bundle.jar"
OUTPUT_DIR="maven-central-artifacts/v${VERSION}"

echo "=========================================="
echo "Maven Central Bundle Creator"
echo "Grant Library v${VERSION}"
echo "=========================================="
echo ""

# Pre-flight checks
echo "🔍 Pre-flight checks..."

if ! command -v gpg &> /dev/null; then
    echo "❌ GPG not installed"
    exit 1
fi

if ! gpg --list-secret-keys 2>/dev/null | grep -q "brewkits\|vietnguyentuan"; then
    echo "❌ No GPG key found"
    exit 1
fi

echo "✅ GPG key found"
echo ""

# Step 1: Build and publish
echo "🔨 Building artifacts..."
./gradlew clean
./gradlew :grant-core:build :grant-compose:build
./gradlew publishAllPublicationsToMavenCentralLocalRepository

echo "✅ Build complete"
echo ""

# Step 2: Generate checksums and signatures
echo "📊 Generating checksums and signatures..."
echo ""

# Save current directory
ROOT_DIR=$(pwd)

# Process grant-core
cd "$ROOT_DIR/grant-core/build/maven-central-staging/$GROUP_PATH"

find . -type f \( -name "*.jar" -o -name "*.pom" -o -name "*.module" \
    -o -name "*.klib" -o -name "*.aar" -o -name "*.json" -o -name "*.zip" \) \
    ! -name "*.asc" ! -name "*.md5" ! -name "*.sha1" | while read file; do

    filename=$(basename "$file")
    echo "  Processing: $filename"

    # MD5
    [ ! -f "$file.md5" ] && (md5 -q "$file" 2>/dev/null || md5sum "$file" | awk '{print $1}') > "$file.md5"

    # SHA1
    [ ! -f "$file.sha1" ] && shasum -a 1 "$file" | awk '{print $1}' > "$file.sha1"

    # SHA256
    [ ! -f "$file.sha256" ] && shasum -a 256 "$file" | awk '{print $1}' > "$file.sha256"

    # SHA512
    [ ! -f "$file.sha512" ] && shasum -a 512 "$file" | awk '{print $1}' > "$file.sha512"

    # GPG signature
    [ ! -f "$file.asc" ] && gpg --batch --yes --pinentry-mode loopback --armor --detach-sign "$file" 2>/dev/null
done

# Process grant-compose
cd "$ROOT_DIR/grant-compose/build/maven-central-staging/$GROUP_PATH"

find . -type f \( -name "*.jar" -o -name "*.pom" -o -name "*.module" \
    -o -name "*.klib" -o -name "*.aar" -o -name "*.json" -o -name "*.zip" \) \
    ! -name "*.asc" ! -name "*.md5" ! -name "*.sha1" | while read file; do

    filename=$(basename "$file")
    echo "  Processing: $filename"

    # MD5
    [ ! -f "$file.md5" ] && (md5 -q "$file" 2>/dev/null || md5sum "$file" | awk '{print $1}') > "$file.md5"

    # SHA1
    [ ! -f "$file.sha1" ] && shasum -a 1 "$file" | awk '{print $1}' > "$file.sha1"

    # SHA256
    [ ! -f "$file.sha256" ] && shasum -a 256 "$file" | awk '{print $1}' > "$file.sha256"

    # SHA512
    [ ! -f "$file.sha512" ] && shasum -a 512 "$file" | awk '{print $1}' > "$file.sha512"

    # GPG signature
    [ ! -f "$file.asc" ] && gpg --batch --yes --pinentry-mode loopback --armor --detach-sign "$file" 2>/dev/null
done

cd "$ROOT_DIR"

echo ""
echo "✅ Checksums and signatures generated!"
echo ""

# Step 3: Create bundle
echo "📦 Creating bundle..."
echo ""

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

TEMP_BUNDLE="$OUTPUT_DIR/temp-bundle"
rm -rf "$TEMP_BUNDLE"
mkdir -p "$TEMP_BUNDLE"

# Copy grant-core artifacts
echo "  Copying: grant-core"
find grant-core/build/maven-central-staging/$GROUP_PATH -mindepth 1 -maxdepth 1 -type d | while read variant; do
    variant_name=$(basename "$variant")
    mkdir -p "$TEMP_BUNDLE/$GROUP_PATH/$variant_name/$VERSION"
    cp -r "$variant/$VERSION/"* "$TEMP_BUNDLE/$GROUP_PATH/$variant_name/$VERSION/" 2>/dev/null || true
done

# Copy grant-compose artifacts
echo "  Copying: grant-compose"
find grant-compose/build/maven-central-staging/$GROUP_PATH -mindepth 1 -maxdepth 1 -type d | while read variant; do
    variant_name=$(basename "$variant")
    mkdir -p "$TEMP_BUNDLE/$GROUP_PATH/$variant_name/$VERSION"
    cp -r "$variant/$VERSION/"* "$TEMP_BUNDLE/$GROUP_PATH/$variant_name/$VERSION/" 2>/dev/null || true
done

echo ""
echo "  Creating ZIP bundle..."
cd "$TEMP_BUNDLE"
zip -q -r "../$BUNDLE_NAME" "$GROUP_PATH/"
cd ../..

BUNDLE_SIZE=$(du -h "$OUTPUT_DIR/$BUNDLE_NAME" | awk '{print $1}')
echo ""
echo "✅ Bundle created: $OUTPUT_DIR/$BUNDLE_NAME ($BUNDLE_SIZE)"
echo ""

# Step 4: Verify
echo "🔍 Verifying bundle..."
echo ""

if unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" 2>/dev/null | grep -q "META-INF"; then
    echo "  ❌ ERROR: Bundle contains META-INF"
    exit 1
else
    echo "  ✅ No META-INF directory"
fi

FILE_COUNT=$(unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" 2>/dev/null | tail -1 | awk '{print $2}')
SIG_COUNT=$(unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" 2>/dev/null | grep "\.asc$" | wc -l | xargs)

echo "  📊 Total files: $FILE_COUNT"
echo "  🔐 Signatures: $SIG_COUNT"
echo ""

# Cleanup
rm -rf "$TEMP_BUNDLE"

# Summary
echo "=========================================="
echo "✅ Maven Central bundle ready!"
echo "=========================================="
echo ""
echo "Bundle Details:"
echo "  Location: $OUTPUT_DIR/$BUNDLE_NAME"
echo "  Size: $BUNDLE_SIZE"
echo "  Files: $FILE_COUNT"
echo "  Signatures: $SIG_COUNT"
echo ""
echo "Next Steps:"
echo "  1. Go to: https://central.sonatype.com/"
echo "  2. Login with Sonatype account"
echo "  3. Publishing → Upload via Publisher Portal"
echo "  4. Upload: $BUNDLE_NAME"
echo ""
echo "=========================================="
echo ""
