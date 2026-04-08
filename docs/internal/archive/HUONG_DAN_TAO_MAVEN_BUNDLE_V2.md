# Hướng Dẫn Tạo Maven Central Bundle - KMP WorkManager

**Tài liệu này:** Chi tiết đầy đủ các bước tạo Maven Central bundle cho KMP WorkManager
**Mục đích:** Để sử dụng lại cho các version tiếp theo (v2.3.0, v2.4.0, ...)
**Đã test:** v2.2.0 - Upload thành công lên Maven Central

---

## Tổng Quan Quy Trình

```
1. Update version trong build.gradle.kts
2. Clean build directory
3. Build và publish artifacts to local staging
4. Tạo checksums (MD5, SHA1, SHA256, SHA512)
5. Tạo GPG signatures (.asc) - QUAN TRỌNG: bao gồm kotlin-tooling-metadata.json
6. Verify tất cả signatures
7. Tạo bundle JAR (không có META-INF)
8. Verify bundle
9. Upload lên Maven Central
```

---

## Bước 1: Update Version

**File:** `kmpworker/build.gradle.kts`

```kotlin
// Line 16
group = "dev.brewkits"
version = "2.2.0"  // <-- Update version ở đây

// Line 92-94
pom {
    groupId = "dev.brewkits"
    artifactId = artifactId.replace("kmpworker", "kmpworkmanager")
    version = "2.2.0"  // <-- Update version ở đây
}
```

**Lưu ý:** Phải update ở 2 chỗ!

---

## Bước 2: Clean và Build

```bash
# Clean build directory
./gradlew clean

# Build và publish to local staging repository
./gradlew :kmpworker:publishAllPublicationsToMavenCentralLocalRepository
```

**Output location:**
```
kmpworker/build/maven-central-staging/dev/brewkits/
├── kmpworkmanager/
├── kmpworkmanager-android/
├── kmpworkmanager-iosarm64/
├── kmpworkmanager-iosx64/
└── kmpworkmanager-iossimulatorarm64/
```

**Kiểm tra build thành công:**
```bash
ls -la kmpworker/build/maven-central-staging/dev/brewkits/
# Expected: 5 directories
```

---

## Bước 3: Tạo Checksums và Signatures

### 3.1. Script Tự Động (Khuyến nghị)

**File:** `create-maven-bundle.sh`

```bash
#!/bin/bash

set -e

VERSION="2.2.0"  # <-- Update version ở đây
GROUP_PATH="dev/brewkits"
STAGING_DIR="kmpworker/build/maven-central-staging"
BUNDLE_NAME="kmpworkmanager-v${VERSION}-bundle.jar"
OUTPUT_DIR="maven-central-artifacts/v${VERSION}"

echo "=========================================="
echo "Maven Central Bundle Creator - v${VERSION}"
echo "=========================================="
echo ""

# Check GPG
if ! command -v gpg &> /dev/null; then
    echo "❌ ERROR: GPG is not installed"
    echo "Install: brew install gnupg"
    exit 1
fi

if ! gpg --list-secret-keys 2>/dev/null | grep -q "brewkits\|vietnguyentuan"; then
    echo "❌ ERROR: No GPG key found"
    exit 1
fi

echo "✅ GPG key found"
echo ""

# Navigate to staging directory
cd "$STAGING_DIR/$GROUP_PATH"

echo "📊 Generating checksums and signatures..."
echo ""

# Generate checksums and signatures for ALL artifacts
# INCLUDING: .jar, .pom, .module, .klib, .aar, .json, .zip
# IMPORTANT: .zip files are kotlin_resources.kotlin_resources.zip (iOS resources)
find . -type f \( -name "*.jar" -o -name "*.pom" -o -name "*.module" \
    -o -name "*.klib" -o -name "*.aar" -o -name "*.json" -o -name "*.zip" \) \
    ! -name "*.asc" ! -name "*.md5" ! -name "*.sha1" \
    ! -name "*.sha256" ! -name "*.sha512" | while read file; do

    echo "Processing: $file"

    # MD5
    if [ ! -f "$file.md5" ]; then
        md5sum "$file" | awk '{print $1}' > "$file.md5"
    fi

    # SHA1
    if [ ! -f "$file.sha1" ]; then
        shasum -a 1 "$file" | awk '{print $1}' > "$file.sha1"
    fi

    # SHA256
    if [ ! -f "$file.sha256" ]; then
        shasum -a 256 "$file" | awk '{print $1}' > "$file.sha256"
    fi

    # SHA512
    if [ ! -f "$file.sha512" ]; then
        shasum -a 512 "$file" | awk '{print $1}' > "$file.sha512"
    fi

    # GPG signature (batch mode để tránh lỗi tty)
    if [ ! -f "$file.asc" ]; then
        gpg --batch --yes --pinentry-mode loopback --armor --detach-sign "$file"
    fi
done

echo ""
echo "✅ All checksums and signatures generated!"
echo ""

# Verify signatures
echo "🔍 Verifying signatures..."
find . -name "*.asc" | while read sigfile; do
    original="${sigfile%.asc}"
    if gpg --verify "$sigfile" "$original" 2>&1 | grep -q "Good signature"; then
        echo "  ✅ $(basename $original)"
    else
        echo "  ❌ $(basename $original) - VERIFICATION FAILED!"
        exit 1
    fi
done

echo ""
echo "✅ All signatures verified!"
echo ""

# Go back to project root
cd ../../../../..

# Create output directory
rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

# Create bundle structure
echo "📦 Creating Maven Central bundle..."
TEMP_BUNDLE="$OUTPUT_DIR/temp-bundle"
rm -rf "$TEMP_BUNDLE"
mkdir -p "$TEMP_BUNDLE"

# Copy all artifacts to bundle
for variant in kmpworkmanager kmpworkmanager-android kmpworkmanager-iosarm64 \
               kmpworkmanager-iosx64 kmpworkmanager-iossimulatorarm64; do
    if [ -d "$STAGING_DIR/$GROUP_PATH/$variant" ]; then
        echo "  Bundling: $variant"
        mkdir -p "$TEMP_BUNDLE/$GROUP_PATH/$variant/$VERSION"
        cp -r "$STAGING_DIR/$GROUP_PATH/$variant/$VERSION/"* \
              "$TEMP_BUNDLE/$GROUP_PATH/$variant/$VERSION/"
    fi
done

# Create ZIP bundle (KHÔNG dùng jar command vì nó tạo META-INF)
cd "$TEMP_BUNDLE"
zip -q -r "../$BUNDLE_NAME" "$GROUP_PATH/"
cd ../..

echo ""
echo "✅ Bundle created: $OUTPUT_DIR/$BUNDLE_NAME"
echo ""

# Verify bundle
echo "🔍 Verifying bundle..."

# Check no META-INF
if unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" | grep -q "META-INF"; then
    echo "  ❌ ERROR: Bundle contains META-INF directory"
    exit 1
else
    echo "  ✅ No META-INF found"
fi

# Check file count
FILE_COUNT=$(unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" | tail -1 | awk '{print $2}')
echo "  📊 Total files: $FILE_COUNT"

# Check bundle size
BUNDLE_SIZE=$(du -h "$OUTPUT_DIR/$BUNDLE_NAME" | awk '{print $1}')
echo "  📦 Bundle size: $BUNDLE_SIZE"

# Verify kotlin-tooling-metadata.json has signature
if ! unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" | grep -q "kotlin-tooling-metadata.json.asc"; then
    echo "  ❌ ERROR: Missing kotlin-tooling-metadata.json.asc"
    exit 1
else
    echo "  ✅ kotlin-tooling-metadata.json.asc present"
fi

# Count signatures
SIG_COUNT=$(unzip -l "$OUTPUT_DIR/$BUNDLE_NAME" | grep "\.asc$" | wc -l | xargs)
echo "  🔐 GPG signatures: $SIG_COUNT files"

echo ""
echo "=========================================="
echo "✅ Maven Central bundle ready!"
echo "=========================================="
echo ""
echo "Bundle location:"
echo "  $OUTPUT_DIR/$BUNDLE_NAME"
echo ""
echo "Next steps:"
echo "  1. Go to: https://central.sonatype.com/"
echo "  2. Click: 'Publishing' → 'Upload via Publisher Portal'"
echo "  3. Upload: $BUNDLE_NAME"
echo "  4. Wait ~10 minutes for validation"
echo "  5. Check: https://central.sonatype.com/artifact/dev.brewkits/kmpworkmanager/$VERSION"
echo ""
echo "=========================================="

# Cleanup temp directory
rm -rf "$TEMP_BUNDLE"
```

**Chạy script:**
```bash
chmod +x create-maven-bundle.sh
./create-maven-bundle.sh
```

---

## Bước 4: Các Files Phải Có Signature

**QUAN TRỌNG:** Tất cả các file sau phải có GPG signature (.asc):

### Core Platform (kmpworkmanager)
- ✅ `kmpworkmanager-2.2.0.jar.asc`
- ✅ `kmpworkmanager-2.2.0-sources.jar.asc`
- ✅ `kmpworkmanager-2.2.0.pom.asc`
- ✅ `kmpworkmanager-2.2.0.module.asc`
- ✅ **`kmpworkmanager-2.2.0-kotlin-tooling-metadata.json.asc`** ← Dễ thiếu!

### Android Platform
- ✅ `kmpworkmanager-android-2.2.0.aar.asc`
- ✅ `kmpworkmanager-android-2.2.0-sources.jar.asc`
- ✅ `kmpworkmanager-android-2.2.0.pom.asc`
- ✅ `kmpworkmanager-android-2.2.0.module.asc`

### iOS ARM64 Platform
- ✅ `kmpworkmanager-iosarm64-2.2.0.klib.asc`
- ✅ `kmpworkmanager-iosarm64-2.2.0-sources.jar.asc`
- ✅ `kmpworkmanager-iosarm64-2.2.0-metadata.jar.asc`
- ✅ `kmpworkmanager-iosarm64-2.2.0.pom.asc`
- ✅ `kmpworkmanager-iosarm64-2.2.0.module.asc`
- ✅ `kmpworkmanager-iosarm64-2.2.0-kotlin_resources.kotlin_resources.zip.asc` ← **Chỉ có nếu dùng Compose!**

### iOS x64 Platform (tương tự ARM64)
- 5-6 files .asc (6 nếu có Compose resources)

### iOS Simulator ARM64 Platform (tương tự ARM64)
- 5-6 files .asc (6 nếu có Compose resources)

**Tổng cộng:**
- KMP thông thường: 24 GPG signatures
- KMP + Compose: 27 GPG signatures (thêm 3 file .zip.asc cho iOS)

---

## Bước 5: Vấn Đề Thường Gặp

### Lỗi 1: "Missing signature for file: kotlin-tooling-metadata.json"

**Nguyên nhân:** Script không sign file `.json`

**Giải pháp:** Script phải include `-o -name "*.json"` trong find command:

```bash
find . -type f \( -name "*.jar" -o -name "*.pom" -o -name "*.module" \
    -o -name "*.klib" -o -name "*.aar" -o -name "*.json" \) \
    ! -name "*.asc" ...
```

**Fix thủ công nếu thiếu:**
```bash
cd kmpworker/build/maven-central-staging/dev/brewkits/kmpworkmanager/2.2.0
gpg --batch --yes --pinentry-mode loopback --armor --detach-sign \
    kmpworkmanager-2.2.0-kotlin-tooling-metadata.json

# Verify
gpg --verify kmpworkmanager-2.2.0-kotlin-tooling-metadata.json.asc \
             kmpworkmanager-2.2.0-kotlin-tooling-metadata.json
# Expected: "Good signature"
```

### Lỗi 2: "Bundle contains META-INF"

**Nguyên nhân:** Dùng `jar -cvf` thay vì `zip`

**Giải pháp:** Luôn dùng `zip` command:
```bash
cd temp-bundle
zip -q -r ../bundle.jar dev/
```

**KHÔNG dùng:**
```bash
jar -cvf bundle.jar dev/  # ❌ Tạo META-INF
```

### Lỗi 3: "Missing signature for file: kotlin_resources.kotlin_resources.zip"

**Nguyên nhân:** Script không sign file `.zip` (iOS resources)

**Chi tiết:** Compose Multiplatform iOS platforms tự động generate file `*-kotlin_resources.kotlin_resources.zip` chứa resources. File này CŨng cần signature.

**Giải pháp:** Script phải include `-o -name "*.zip"` trong find command:

```bash
find . -type f \( -name "*.jar" -o -name "*.pom" -o -name "*.module" \
    -o -name "*.klib" -o -name "*.aar" -o -name "*.json" -o -name "*.zip" \) \
    ! -name "*.asc" ...
```

**Fix thủ công nếu thiếu:**
```bash
# Find all .zip files in staging
find . -name "*kotlin_resources.kotlin_resources.zip" | while read file; do
    # Generate checksums
    md5sum "$file" | awk '{print $1}' > "$file.md5"
    shasum -a 1 "$file" | awk '{print $1}' > "$file.sha1"
    shasum -a 256 "$file" | awk '{print $1}' > "$file.sha256"
    shasum -a 512 "$file" | awk '{print $1}' > "$file.sha512"

    # Generate signature
    gpg --batch --yes --pinentry-mode loopback --armor --detach-sign "$file"
done
```

**Platforms có .zip files:**
- ❌ Core platform: KHÔNG có
- ❌ Android: KHÔNG có
- ✅ iOS ARM64: CÓ (nếu dùng Compose)
- ✅ iOS x64: CÓ (nếu dùng Compose)
- ✅ iOS Simulator ARM64: CÓ (nếu dùng Compose)

### Lỗi 4: GPG không ký được (Device not configured)

**Nguyên nhân:** GPG cần interactive terminal

**Giải pháp:** Dùng batch mode:
```bash
gpg --batch --yes --pinentry-mode loopback --armor --detach-sign file.jar
```

---

## Bước 6: Verify Bundle Trước Khi Upload

```bash
VERSION="2.2.0"
BUNDLE="maven-central-artifacts/v${VERSION}/kmpworkmanager-v${VERSION}-bundle.jar"

# 1. Kiểm tra không có META-INF
unzip -l "$BUNDLE" | grep -i "meta-inf" && echo "❌ FAIL" || echo "✅ PASS"

# 2. Kiểm tra số lượng files
FILE_COUNT=$(unzip -l "$BUNDLE" | tail -1 | awk '{print $2}')
echo "Total files: $FILE_COUNT"
# Expected: 156 files

# 3. Kiểm tra kotlin-tooling-metadata.json.asc
unzip -l "$BUNDLE" | grep "kotlin-tooling-metadata.json.asc" && \
    echo "✅ kotlin-tooling-metadata.json.asc present" || \
    echo "❌ Missing kotlin-tooling-metadata.json.asc"

# 4. Đếm số signatures
SIG_COUNT=$(unzip -l "$BUNDLE" | grep "\.asc$" | wc -l | xargs)
echo "GPG signatures: $SIG_COUNT"
# Expected: 24 files

# 5. Kiểm tra bundle size
du -h "$BUNDLE"
# Expected: ~1.3MB
```

**Checklist trước upload:**
- [ ] Không có thư mục META-INF
- [ ] Có 156 files
- [ ] Có 24 GPG signatures (.asc)
- [ ] File kotlin-tooling-metadata.json.asc tồn tại
- [ ] Bundle size ~1.3MB

---

## Bước 7: Upload Lên Maven Central

### Upload via Web Portal

1. Login: https://central.sonatype.com/
2. Click: **Publishing** → **Upload via Publisher Portal**
3. Click: **Choose Bundle**
4. Select: `kmpworkmanager-v2.2.0-bundle.jar`
5. Click: **Upload Bundle**

### Validation Process

- Validation thời gian: **2-10 phút**
- Nếu PASS: Bundle tự động publish
- Artifacts xuất hiện sau: **15-30 phút**

### Verify Publication

**Sonatype Central:**
```
https://central.sonatype.com/artifact/dev.brewkits/kmpworkmanager/2.2.0
```

**Maven Central Repository:**
```
https://repo1.maven.org/maven2/dev/brewkits/kmpworkmanager/2.2.0/
```

---

## Bước 8: Sử Dụng Artifact (Sau Khi Publish)

**Gradle (Kotlin DSL):**
```kotlin
// In libs.versions.toml
[versions]
kmpworkmanager = "2.2.0"

[libraries]
kmpworkmanager = { module = "dev.brewkits:kmpworkmanager", version.ref = "kmpworkmanager" }

// In build.gradle.kts
commonMain.dependencies {
    implementation(libs.kmpworkmanager)
}
```

**Hoặc direct dependency:**
```kotlin
commonMain.dependencies {
    implementation("dev.brewkits:kmpworkmanager:2.2.0")
}
```

---

## Tổng Kết: Checklist Đầy Đủ

### Pre-Build
- [ ] Update version trong `kmpworker/build.gradle.kts` (2 chỗ)
- [ ] Kiểm tra GPG key: `gpg --list-secret-keys`

### Build
- [ ] `./gradlew clean`
- [ ] `./gradlew :kmpworker:publishAllPublicationsToMavenCentralLocalRepository`
- [ ] Verify: 5 directories trong `kmpworker/build/maven-central-staging/dev/brewkits/`

### Checksums & Signatures
- [ ] Tạo checksums: MD5, SHA1, SHA256, SHA512
- [ ] Tạo GPG signatures cho TẤT CẢ files (bao gồm .json)
- [ ] Verify: 24 signature files (.asc)
- [ ] Đặc biệt kiểm tra: `kotlin-tooling-metadata.json.asc`

### Bundle
- [ ] Tạo bundle với `zip` (không dùng `jar`)
- [ ] Verify: Không có META-INF
- [ ] Verify: 156 files total
- [ ] Verify: 24 GPG signatures
- [ ] Verify: ~1.3MB size

### Upload
- [ ] Upload lên https://central.sonatype.com/
- [ ] Đợi validation (2-10 phút)
- [ ] Verify publication sau 15-30 phút

---

## Files Tham Khảo

**Script tự động:** `create-maven-bundle.sh`
**Bundle location:** `maven-central-artifacts/v2.2.0/kmpworkmanager-v2.2.0-bundle.jar`
**Documentation:** `maven-central-artifacts/v2.2.0/MAVEN_ARTIFACTS_v2.2.0.md`

---

## Lưu Ý Quan Trọng

1. **LUÔN sign file .json**: kotlin-tooling-metadata.json cần có .asc
2. **LUÔN dùng zip**: Không dùng jar command để tạo bundle
3. **LUÔN verify trước upload**: Kiểm tra 24 signatures và không có META-INF
4. **GPG batch mode**: Dùng `--batch --yes --pinentry-mode loopback` để tránh lỗi tty
5. **Version consistency**: Update version ở 2 chỗ trong build.gradle.kts

---

**Script này đã được test thành công với v2.2.0**

**Sử dụng cho version tiếp theo:**
1. Thay đổi `VERSION="2.3.0"` trong script
2. Update `build.gradle.kts` (2 chỗ)
3. Chạy script: `./create-maven-bundle.sh`
4. Upload bundle lên Maven Central

✅ **Hoàn thành!**
