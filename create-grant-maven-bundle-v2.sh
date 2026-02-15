#!/bin/bash

# Maven Central Bundle Creator for Grant v1.0.1
set -e

VERSION="1.0.1"
GROUP_PATH="dev/brewkits"
BUNDLE_NAME="grant-v${VERSION}-bundle.jar"
OUTPUT_DIR="maven-central-artifacts/v${VERSION}"

echo "=========================================="
echo "Grant v${VERSION} Maven Bundle Creator"
echo "=========================================="
echo ""

# Check GPG
if ! command -v gpg &> /dev/null; then
    echo "❌ GPG not installed"
    exit 1
fi

echo "✅ GPG found"
echo ""

# Create output directory
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"
TEMP_BUNDLE="$OUTPUT_DIR/temp-bundle"
mkdir -p "$TEMP_BUNDLE/$GROUP_PATH"

echo "📦 Collecting artifacts..."
echo ""

# Copy grant-core artifacts
for variant in grant-core grant-core-android grant-core-iosarm64 grant-core-iossimulatorarm64 grant-core-iosx64; do
    SRC="grant-core/build/maven-central-staging/$GROUP_PATH/$variant/$VERSION"
    if [ -d "$SRC" ]; then
        echo "  ✅ $variant"
        mkdir -p "$TEMP_BUNDLE/$GROUP_PATH/$variant/$VERSION"
        cp -r "$SRC/"* "$TEMP_BUNDLE/$GROUP_PATH/$variant/$VERSION/"
    fi
done

# Copy grant-compose artifacts  
for variant in grant-compose grant-compose-android grant-compose-iosarm64 grant-compose-iossimulatorarm64 grant-compose-iosx64; do
    SRC="grant-compose/build/maven-central-staging/$GROUP_PATH/$variant/$VERSION"
    if [ -d "$SRC" ]; then
        echo "  ✅ $variant"
        mkdir -p "$TEMP_BUNDLE/$GROUP_PATH/$variant/$VERSION"
        cp -r "$SRC/"* "$TEMP_BUNDLE/$GROUP_PATH/$variant/$VERSION/"
    fi
done

echo ""
echo "📊 Generating checksums and signatures..."
echo ""

cd "$TEMP_BUNDLE"

# Generate checksums and signatures for all artifacts
find . -type f \( -name "*.jar" -o -name "*.pom" -o -name "*.module" \
    -o -name "*.klib" -o -name "*.aar" -o -name "*.json" -o -name "*.zip" \) \
    ! -name "*.asc" ! -name "*.md5" ! -name "*.sha1" \
    ! -name "*.sha256" ! -name "*.sha512" | while read file; do
    
    filename=$(basename "$file")
    
    # Generate checksums
    md5sum "$file" | awk '{print $1}' > "$file.md5"
    shasum -a 1 "$file" | awk '{print $1}' > "$file.sha1"
    shasum -a 256 "$file" | awk '{print $1}' > "$file.sha256"
    shasum -a 512 "$file" | awk '{print $1}' > "$file.sha512"
    
    # Generate GPG signature
    gpg --batch --yes --pinentry-mode loopback --armor --detach-sign "$file" 2>/dev/null
    
    echo "  ✅ $filename"
done

cd ..

echo ""
echo "📦 Creating bundle ZIP..."

cd "$TEMP_BUNDLE"
zip -q -r "../$BUNDLE_NAME" "$GROUP_PATH/"
cd ../..

echo ""
echo "✅ Bundle created: $OUTPUT_DIR/$BUNDLE_NAME"
echo ""

# Verify
FILE_COUNT=$(unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" 2>/dev/null | tail -1 | awk '{print $2}')
SIG_COUNT=$(unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" 2>/dev/null | grep "\.asc$" | wc -l | xargs)
BUNDLE_SIZE=$(du -h "$OUTPUT_DIR/$BUNDLE_NAME" | awk '{print $1}')

echo "Bundle Info:"
echo "  📦 Size: $BUNDLE_SIZE"
echo "  📊 Files: $FILE_COUNT"
echo "  🔐 Signatures: $SIG_COUNT"
echo ""

# Check for META-INF
if unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" 2>/dev/null | grep -q "META-INF"; then
    echo "⚠️  WARNING: Bundle contains META-INF"
else
    echo "✅ No META-INF directory"
fi

echo ""
echo "Ready to upload to Maven Central!"
echo "Location: $OUTPUT_DIR/$BUNDLE_NAME"
echo ""

# Cleanup
rm -rf "$TEMP_BUNDLE"
