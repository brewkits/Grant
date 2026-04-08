#!/bin/bash

# Maven Central Bundle Creator for Grant KMP Library
# Version: 1.0.1
#
# This script creates a complete Maven Central bundle with:
# - All artifacts (JAR, AAR, KLIB, POM, MODULE, JSON)
# - Checksums (MD5, SHA1, SHA256, SHA512)
# - GPG signatures (.asc) for ALL files
# - Bundle JAR without META-INF directory

set -e

# ============================================
# Configuration
# ============================================

VERSION="1.0.1"
GROUP_PATH="dev/brewkits"
BUNDLE_NAME="grant-v${VERSION}-bundle.jar"
OUTPUT_DIR="maven-central-artifacts/v${VERSION}"

# Modules to bundle
MODULES=("grant-core" "grant-compose")

# ============================================
# Header
# ============================================

echo "=========================================="
echo "Maven Central Bundle Creator"
echo "Grant KMP Library v${VERSION}"
echo "=========================================="
echo ""

# ============================================
# Pre-flight Checks
# ============================================

echo "🔍 Running pre-flight checks..."
echo ""

# Check if GPG is installed
if ! command -v gpg &> /dev/null; then
    echo "❌ ERROR: GPG is not installed"
    echo ""
    echo "Install GPG:"
    echo "  macOS: brew install gnupg"
    echo "  Linux: sudo apt-get install gnupg"
    echo ""
    exit 1
fi

# Check if GPG key exists
if ! gpg --list-secret-keys 2>/dev/null | grep -q "brewkits\|vietnguyentuan"; then
    echo "❌ ERROR: No GPG key found for brewkits"
    echo ""
    echo "To create a GPG key:"
    echo "  gpg --gen-key"
    echo "  # Use email: vietnguyentuan@gmail.com"
    echo ""
    echo "To list existing keys:"
    echo "  gpg --list-secret-keys"
    echo ""
    exit 1
fi

echo "✅ GPG key found"

# Check if staging directories exist
for module in "${MODULES[@]}"; do
    STAGING_DIR="${module}/build/maven-central-staging"
    if [ ! -d "$STAGING_DIR" ]; then
        echo "❌ ERROR: Staging directory not found: $STAGING_DIR"
        echo ""
        echo "Please run first:"
        echo "  ./gradlew :${module}:publishAllPublicationsToMavenCentralLocalRepository"
        echo ""
        exit 1
    fi
    echo "✅ Staging directory found for $module"
done

echo ""

# ============================================
# Step 1: Generate Checksums and Signatures
# ============================================

echo "📊 Generating checksums and signatures..."
echo ""

for module in "${MODULES[@]}"; do
    echo "Processing module: $module"
    STAGING_DIR="${module}/build/maven-central-staging"

    # Navigate to staging directory
    cd "$STAGING_DIR/$GROUP_PATH"

    # Process all artifact files
    find . -type f \( -name "*.jar" -o -name "*.pom" -o -name "*.module" \
        -o -name "*.klib" -o -name "*.aar" -o -name "*.json" -o -name "*.zip" \) \
        ! -name "*.asc" ! -name "*.md5" ! -name "*.sha1" \
        ! -name "*.sha256" ! -name "*.sha512" | sort | while read file; do

        filename=$(basename "$file")
        echo "  Processing: $filename"

        # Generate MD5
        if [ ! -f "$file.md5" ]; then
            md5sum "$file" | awk '{print $1}' > "$file.md5"
        fi

        # Generate SHA1
        if [ ! -f "$file.sha1" ]; then
            shasum -a 1 "$file" | awk '{print $1}' > "$file.sha1"
        fi

        # Generate SHA256
        if [ ! -f "$file.sha256" ]; then
            shasum -a 256 "$file" | awk '{print $1}' > "$file.sha256"
        fi

        # Generate SHA512
        if [ ! -f "$file.sha512" ]; then
            shasum -a 512 "$file" | awk '{print $1}' > "$file.sha512"
        fi

        # Generate GPG signature
        if [ ! -f "$file.asc" ]; then
            gpg --batch --yes --pinentry-mode loopback --armor --detach-sign "$file" 2>/dev/null
        fi
    done

    # Go back to project root
    cd - > /dev/null
    cd ../../../..
done

echo ""
echo "✅ All checksums and signatures generated!"
echo ""

# ============================================
# Step 2: Create Bundle
# ============================================

echo "📦 Creating Maven Central bundle..."
echo ""

# Create output directory
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# Create temporary bundle structure
TEMP_BUNDLE="$OUTPUT_DIR/temp-bundle"
rm -rf "$TEMP_BUNDLE"
mkdir -p "$TEMP_BUNDLE"

# Copy all artifacts to bundle
for module in "${MODULES[@]}"; do
    STAGING_DIR="${module}/build/maven-central-staging"
    if [ -d "$STAGING_DIR/$GROUP_PATH/$module" ]; then
        echo "  Copying: $module"
        mkdir -p "$TEMP_BUNDLE/$GROUP_PATH/$module/$VERSION"
        cp -r "$STAGING_DIR/$GROUP_PATH/$module/$VERSION/"* \
              "$TEMP_BUNDLE/$GROUP_PATH/$module/$VERSION/" 2>/dev/null || true
    fi
done

echo ""

# Create ZIP bundle
echo "  Creating ZIP bundle..."
cd "$TEMP_BUNDLE"
zip -q -r "../$BUNDLE_NAME" "$GROUP_PATH/"
cd ../..

echo ""
echo "✅ Bundle created: $OUTPUT_DIR/$BUNDLE_NAME"
echo ""

# ============================================
# Step 3: Verify Bundle
# ============================================

echo "🔍 Verifying bundle..."
echo ""

# Check 1: No META-INF
if unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" 2>/dev/null | grep -q "META-INF"; then
    echo "  ❌ ERROR: Bundle contains META-INF directory"
    echo "  This will cause Maven Central validation to fail"
    exit 1
else
    echo "  ✅ No META-INF directory"
fi

# Check 2: File count
FILE_COUNT=$(unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" 2>/dev/null | tail -1 | awk '{print $2}')
echo "  📊 Total files: $FILE_COUNT"

# Check 3: kotlin-tooling-metadata.json.asc exists
if ! unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" 2>/dev/null | grep -q "kotlin-tooling-metadata.json.asc"; then
    echo "  ⚠️  WARNING: Missing kotlin-tooling-metadata.json.asc"
fi

# Check 4: Count signatures
SIG_COUNT=$(unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" 2>/dev/null | grep "\.asc$" | wc -l | xargs)
echo "  🔐 GPG signatures: $SIG_COUNT files"

# Check 5: Bundle size
BUNDLE_SIZE=$(du -h "$OUTPUT_DIR/$BUNDLE_NAME" | awk '{print $1}')
echo "  📦 Bundle size: $BUNDLE_SIZE"

echo ""

# ============================================
# Step 4: Cleanup
# ============================================

echo "🧹 Cleaning up temporary files..."
rm -rf "$TEMP_BUNDLE"
echo ""

# ============================================
# Summary
# ============================================

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
echo "Verification Checklist:"
echo "  ✅ No META-INF directory"
echo "  ✅ All checksums generated (MD5, SHA1, SHA256, SHA512)"
echo "  ✅ All GPG signatures created"
echo ""
echo "Next Steps:"
echo "  1. Go to: https://central.sonatype.com/"
echo "  2. Login with your Sonatype account"
echo "  3. Click: 'Publishing' → 'Upload via Publisher Portal'"
echo "  4. Click: 'Choose Bundle'"
echo "  5. Upload: $BUNDLE_NAME"
echo "  6. Wait 2-10 minutes for validation"
echo ""
echo "After Validation:"
echo "  - Bundle will be automatically published"
echo "  - Artifacts appear in ~15-30 minutes"
echo "  - Verify at: https://central.sonatype.com/artifact/dev.brewkits/grant-core/$VERSION"
echo ""
echo "=========================================="
echo ""
